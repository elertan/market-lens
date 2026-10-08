package com.marketlens.price;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.Value;

/**
 * Caches wiki price data and decides when to refresh it.
 * Safe to read from any thread; {@link #onUpdate} fires on the OkHttp thread whenever new data arrives.
 */
@Singleton
public class PriceService
{
	private static final long LATEST_TTL_MS = TimeUnit.SECONDS.toMillis(60);
	private static final long HOURLY_TTL_MS = TimeUnit.MINUTES.toMillis(5);
	private static final long SERIES_TTL_MS = TimeUnit.MINUTES.toMillis(5);
	private static final long SERIES_RETRY_MS = TimeUnit.SECONDS.toMillis(5);
	private static final int MAX_CACHED_SERIES = 32;

	public enum Status
	{
		LOADING,
		READY,
		FAILED
	}

	@Value
	public static class Series
	{
		Status status;
		List<TimeseriesPoint> points;
		long fetchedAt;
	}

	private final WikiPriceClient client;

	private volatile Map<Integer, LatestPrice> latest = Collections.emptyMap();
	private volatile Map<Integer, HourlyVolume> hourly = Collections.emptyMap();
	private volatile Map<Integer, ItemMapping> mapping = Collections.emptyMap();
	private volatile long latestFetchedAt;
	private volatile long hourlyFetchedAt;
	private final AtomicBoolean latestInFlight = new AtomicBoolean();
	private final AtomicBoolean hourlyInFlight = new AtomicBoolean();
	private final AtomicBoolean mappingInFlight = new AtomicBoolean();

	private final Map<String, Series> series = new LinkedHashMap<String, Series>(16, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Series> eldest)
		{
			return size() > MAX_CACHED_SERIES;
		}
	};

	@Setter
	private volatile Runnable onUpdate = () -> {};

	@Inject
	PriceService(WikiPriceClient client)
	{
		this.client = client;
	}

	/** Fetches whatever is stale. Cheap to call often; at most one request per endpoint is in flight. */
	public void refreshIfStale()
	{
		long now = System.currentTimeMillis();

		if (mapping.isEmpty() && mappingInFlight.compareAndSet(false, true))
		{
			client.fetchMapping(
				m -> { mapping = m; mappingInFlight.set(false); onUpdate.run(); },
				() -> mappingInFlight.set(false));
		}

		if (now - latestFetchedAt > LATEST_TTL_MS && latestInFlight.compareAndSet(false, true))
		{
			latestFetchedAt = now;
			client.fetchLatest(
				l -> { latest = l; latestInFlight.set(false); onUpdate.run(); },
				() -> latestInFlight.set(false));
		}

		if (now - hourlyFetchedAt > HOURLY_TTL_MS && hourlyInFlight.compareAndSet(false, true))
		{
			hourlyFetchedAt = now;
			client.fetchHourly(
				h -> { hourly = h; hourlyInFlight.set(false); onUpdate.run(); },
				() -> hourlyInFlight.set(false));
		}
	}

	public LatestPrice getLatest(int itemId)
	{
		return latest.get(itemId);
	}

	public ItemMapping getMapping(int itemId)
	{
		return mapping.get(itemId);
	}

	public HourlyVolume getHourly(int itemId)
	{
		return hourly.get(itemId);
	}

	/** Returns the cached series and starts a fetch when it is missing or stale. */
	public synchronized Series getSeries(int itemId, Timeframe timeframe)
	{
		String key = itemId + ":" + timeframe.getLookback();
		Series cached = series.get(key);
		long now = System.currentTimeMillis();

		boolean stale = cached == null
			|| (cached.status == Status.READY && now - cached.fetchedAt > SERIES_TTL_MS)
			|| (cached.status == Status.FAILED && now - cached.fetchedAt > SERIES_RETRY_MS);
		if (!stale)
		{
			return cached;
		}

		// Keep showing old points while the refresh runs.
		List<TimeseriesPoint> previous = cached == null ? Collections.emptyList() : cached.points;
		Series loading = new Series(Status.LOADING, previous, now);
		series.put(key, loading);

		client.fetchTimeseries(itemId, timeframe,
			points -> complete(key, new Series(Status.READY, points, System.currentTimeMillis())),
			() -> complete(key, new Series(Status.FAILED, previous, System.currentTimeMillis())));
		return loading;
	}

	private void complete(String key, Series result)
	{
		synchronized (this)
		{
			series.put(key, result);
		}
		onUpdate.run();
	}

	public synchronized void clear()
	{
		series.clear();
	}
}
