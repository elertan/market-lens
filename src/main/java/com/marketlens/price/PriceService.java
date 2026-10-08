package com.marketlens.price;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Setter;
import lombok.Value;

/**
 * Caches wiki price data and decides when to fetch it. Data is only fetched for the item the user is looking at
 * (or about to look at), and never more often than it can change:
 * <ul>
 * <li>live price: per item, at most once a minute (the API caches it for 60s); failed fetches retry after 5s</li>
 * <li>price series: per item and lookback, at most once per bucket of that series; failed fetches retry after 5s</li>
 * <li>item names and buy limits: once per session, with growing delays between failed attempts</li>
 * </ul>
 * Safe to use from any thread; {@link #onUpdate} fires on the OkHttp thread whenever new data arrives.
 */
@Singleton
public class PriceService
{
	private static final long LATEST_TTL_MS = TimeUnit.SECONDS.toMillis(60);
	private static final long LATEST_RETRY_MS = TimeUnit.SECONDS.toMillis(5);
	private static final long SERIES_RETRY_MS = TimeUnit.SECONDS.toMillis(5);
	private static final long MAPPING_RETRY_MIN_MS = TimeUnit.SECONDS.toMillis(5);
	private static final long MAPPING_RETRY_MAX_MS = TimeUnit.MINUTES.toMillis(5);
	private static final int MAX_CACHED_SERIES = 32;
	/** "Volume (1h)" is summed from this series: it has 5-minute buckets. */
	private static final Timeframe VOLUME_SERIES = Timeframe.SIX_HOURS;

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
	private final LongSupplier clock;

	private final Map<Integer, LatestPrice> latest = new ConcurrentHashMap<>();
	private final Map<Integer, Long> latestFetchedAt = new ConcurrentHashMap<>();

	private volatile Map<Integer, ItemMapping> mapping = Collections.emptyMap();
	private final AtomicBoolean mappingInFlight = new AtomicBoolean();
	private volatile long mappingRetryAt;
	private volatile long mappingRetryDelay = MAPPING_RETRY_MIN_MS;

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
		this(client, System::currentTimeMillis);
	}

	PriceService(WikiPriceClient client, LongSupplier clock)
	{
		this.client = client;
		this.clock = clock;
	}

	/**
	 * Fetches whatever a Market Lens window for {@code itemId} needs and is stale: item names and buy limits,
	 * the live price, the series behind "Volume (1h)" and the {@code timeframe} series.
	 * Cheap to call often; called while the window is open and when the user hovers the Market Lens button.
	 */
	public void refresh(int itemId, Timeframe timeframe)
	{
		refreshMapping();
		refreshLatest(itemId);
		getSeries(itemId, VOLUME_SERIES);
		getSeries(itemId, timeframe);
	}

	public LatestPrice getLatest(int itemId)
	{
		return latest.get(itemId);
	}

	public ItemMapping getMapping(int itemId)
	{
		return mapping.get(itemId);
	}

	/** Units traded (instant buys and sells) in the last hour, or null until its series has loaded. */
	public Long getHourVolume(int itemId)
	{
		Series volumeSeries;
		synchronized (this)
		{
			volumeSeries = series.get(key(itemId, VOLUME_SERIES));
		}
		if (volumeSeries == null || volumeSeries.points.isEmpty())
		{
			return null;
		}

		long since = TimeUnit.MILLISECONDS.toSeconds(clock.getAsLong()) - TimeUnit.HOURS.toSeconds(1);
		long total = 0;
		for (TimeseriesPoint point : volumeSeries.points)
		{
			if (point.getTimestamp() >= since)
			{
				total += point.totalVolume();
			}
		}
		return total;
	}

	/** Returns the cached series and starts a fetch when it is missing or stale. */
	public synchronized Series getSeries(int itemId, Timeframe timeframe)
	{
		String key = key(itemId, timeframe);
		Series cached = series.get(key);
		long now = clock.getAsLong();

		boolean stale = cached == null
			|| (cached.status == Status.READY && now - cached.fetchedAt > TimeUnit.SECONDS.toMillis(timeframe.getBucketSeconds()))
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
			points -> complete(key, new Series(Status.READY, points, clock.getAsLong())),
			() -> complete(key, new Series(Status.FAILED, previous, clock.getAsLong())));
		return loading;
	}

	/** Drops all cached data, e.g. when the plugin stops. */
	public synchronized void clear()
	{
		series.clear();
		latest.clear();
		latestFetchedAt.clear();
		mapping = Collections.emptyMap();
		mappingRetryAt = 0;
		mappingRetryDelay = MAPPING_RETRY_MIN_MS;
	}

	private void refreshMapping()
	{
		if (!mapping.isEmpty() || clock.getAsLong() < mappingRetryAt || !mappingInFlight.compareAndSet(false, true))
		{
			return;
		}
		client.fetchMapping(
			loaded ->
			{
				mapping = loaded;
				mappingInFlight.set(false);
				onUpdate.run();
			},
			() ->
			{
				// Wait longer after each failure, so an outage doesn't turn into a stream of requests.
				mappingRetryAt = clock.getAsLong() + mappingRetryDelay;
				mappingRetryDelay = Math.min(mappingRetryDelay * 2, MAPPING_RETRY_MAX_MS);
				mappingInFlight.set(false);
			});
	}

	private void refreshLatest(int itemId)
	{
		long now = clock.getAsLong();
		Long fetchedAt = latestFetchedAt.get(itemId);
		if (fetchedAt != null && now - fetchedAt < LATEST_TTL_MS)
		{
			return;
		}
		// Recorded up front: also stops duplicate requests while this one runs, and delays a retry after failure.
		latestFetchedAt.put(itemId, now);
		client.fetchLatest(itemId,
			price ->
			{
				if (price != null)
				{
					latest.put(itemId, price);
				}
				onUpdate.run();
			},
			// Backdate the attempt so the next one comes after the short retry delay, not a full minute.
			() -> latestFetchedAt.put(itemId, clock.getAsLong() - LATEST_TTL_MS + LATEST_RETRY_MS));
	}

	private void complete(String key, Series result)
	{
		synchronized (this)
		{
			series.put(key, result);
		}
		onUpdate.run();
	}

	private static String key(int itemId, Timeframe timeframe)
	{
		return itemId + ":" + timeframe.getLookback();
	}
}
