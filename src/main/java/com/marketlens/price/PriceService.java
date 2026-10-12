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
 * (or about to look at), and no more often than the prices.runescape.wiki website. Nothing is refreshed while the
 * window stays open; only opening the window, switching the timeframe or hovering the Market Lens button fetches
 * data, and only data that can have changed:
 * <ul>
 * <li>live price: per item, if older than a minute (the API caches it for 60s)</li>
 * <li>price series: per item and lookback, if a new bucket can have arrived since the last fetch</li>
 * <li>item names and buy limits: once per session, with growing delays between failed attempts</li>
 * </ul>
 * Like the website, a failed live price or series is retried up to 3 times, but with longer delays (5s, 10s, 20s
 * instead of 1s, 2s, 4s) to give a struggling API time to recover. After that, it waits for the user to open the
 * window or pick a timeframe again.
 * Safe to use from any thread; {@link #onUpdate} fires on the OkHttp thread whenever new data arrives.
 */
@Singleton
public class PriceService
{
	private static final long LATEST_TTL_MS = TimeUnit.SECONDS.toMillis(60);
	/** Delay before the first retry; it doubles for each retry after that. */
	private static final long RETRY_MS = TimeUnit.SECONDS.toMillis(5);
	private static final int MAX_RETRIES = 3;
	private static final long MAPPING_RETRY_MIN_MS = TimeUnit.SECONDS.toMillis(5);
	private static final long MAPPING_RETRY_MAX_MS = TimeUnit.MINUTES.toMillis(5);
	private static final int MAX_CACHED_SERIES = 32;
	/** "Volume (1h)" needs 5-minute buckets: from this series, unless the chart's own series has them. */
	private static final Timeframe VOLUME_SERIES = Timeframe.SIX_HOURS;
	private static final Series MISSING = new Series(Status.LOADING, Collections.emptyList(), 0, 0, 0);

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
		/** When the last fetch started or ended; times the retries and the refetch. */
		long fetchedAt;
		/** Failed fetches in a row. */
		int failures;
		/** When {@link #points} arrived. Unlike {@link #fetchedAt}, kept while a refetch runs or after it fails. */
		long pointsFetchedAt;

		public boolean isRetrying()
		{
			return status == Status.FAILED && failures <= MAX_RETRIES;
		}
	}

	private final WikiPriceClient client;
	private final LongSupplier clock;

	private final Map<Integer, LatestPrice> latest = new ConcurrentHashMap<>();
	private final Map<Integer, Long> latestFetchedAt = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> latestFailures = new ConcurrentHashMap<>();

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
	 * the live price, the {@code timeframe} series and the series behind "Volume (1h)".
	 * Called when the window opens, when the timeframe changes and when the user hovers the Market Lens button.
	 */
	public void refresh(int itemId, Timeframe timeframe)
	{
		refreshMapping();
		refreshLatest(itemId, true);
		refreshSeries(itemId, timeframe, true);
		refreshSeries(itemId, volumeSeries(timeframe), true);
	}

	/**
	 * Retries failed fetches for an open window; never refreshes data that loaded.
	 * Cheap to call often; called every game tick while the window is open.
	 */
	public void retryFailed(int itemId, Timeframe timeframe)
	{
		refreshMapping();
		refreshLatest(itemId, false);
		refreshSeries(itemId, timeframe, false);
		refreshSeries(itemId, volumeSeries(timeframe), false);
	}

	public LatestPrice getLatest(int itemId)
	{
		return latest.get(itemId);
	}

	public ItemMapping getMapping(int itemId)
	{
		return mapping.get(itemId);
	}

	/**
	 * Units traded (instant buys and sells) in the hour before the series was fetched, or null until it has loaded.
	 * {@code timeframe} is the chart's, whose series is used if it has 5-minute buckets.
	 */
	public Long getHourVolume(int itemId, Timeframe timeframe)
	{
		Series volumeSeries = getSeries(itemId, volumeSeries(timeframe));
		if (volumeSeries.points.isEmpty())
		{
			return null;
		}

		// From the fetch time, not now: the series is not refreshed while the window is open.
		long since = TimeUnit.MILLISECONDS.toSeconds(volumeSeries.pointsFetchedAt) - TimeUnit.HOURS.toSeconds(1);
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

	/** Returns the cached series, or an empty loading one. Never fetches: see {@link #refresh}. */
	public synchronized Series getSeries(int itemId, Timeframe timeframe)
	{
		return series.getOrDefault(key(itemId, timeframe), MISSING);
	}

	/** Starts a fetch of the series if it is missing or {@link #isDue due}; a new bucket can arrive once per bucket. */
	private synchronized void refreshSeries(int itemId, Timeframe timeframe, boolean userAction)
	{
		String key = key(itemId, timeframe);
		Series cached = series.get(key);
		long now = clock.getAsLong();
		long maxAge = TimeUnit.SECONDS.toMillis(timeframe.getBucketSeconds());
		if (cached != null && (cached.status == Status.LOADING
			|| !isDue(now - cached.fetchedAt, cached.failures, maxAge, userAction)))
		{
			return;
		}

		// Keep showing old points while the refresh runs. A user action starts a new round of retries.
		List<TimeseriesPoint> previous = cached == null ? Collections.emptyList() : cached.points;
		long previousAt = cached == null ? 0 : cached.pointsFetchedAt;
		int failures = cached == null || userAction ? 0 : cached.failures;
		series.put(key, new Series(Status.LOADING, previous, now, failures, previousAt));

		client.fetchTimeseries(itemId, timeframe,
			points ->
			{
				long at = clock.getAsLong();
				complete(key, new Series(Status.READY, points, at, 0, at));
			},
			() -> complete(key, new Series(Status.FAILED, previous, clock.getAsLong(), failures + 1, previousAt)));
	}

	/** The series "Volume (1h)" is summed from: the chart's own if it has 5-minute buckets, so no extra fetch. */
	private static Timeframe volumeSeries(Timeframe timeframe)
	{
		return timeframe.getBucketSeconds() == VOLUME_SERIES.getBucketSeconds() ? timeframe : VOLUME_SERIES;
	}

	/** Drops all cached data, e.g. when the plugin stops. */
	public synchronized void clear()
	{
		series.clear();
		latest.clear();
		latestFetchedAt.clear();
		latestFailures.clear();
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

	/** Starts a fetch of the live price if it is missing or {@link #isDue due}; the API caches it for 60s. */
	private void refreshLatest(int itemId, boolean userAction)
	{
		long now = clock.getAsLong();
		Long fetchedAt = latestFetchedAt.get(itemId);
		if (fetchedAt != null
			&& !isDue(now - fetchedAt, latestFailures.getOrDefault(itemId, 0), LATEST_TTL_MS, userAction))
		{
			return;
		}

		// Recorded up front: also stops duplicate requests while this one runs. A user action starts a new round
		// of retries.
		latestFetchedAt.put(itemId, now);
		if (userAction)
		{
			latestFailures.remove(itemId);
		}
		client.fetchLatest(itemId,
			price ->
			{
				latestFailures.remove(itemId);
				if (price != null)
				{
					latest.put(itemId, price);
				}
				onUpdate.run();
			},
			() ->
			{
				// Retry delays count from the failure, not from the start of the request.
				latestFetchedAt.put(itemId, clock.getAsLong());
				latestFailures.merge(itemId, 1, Integer::sum);
			});
	}

	/**
	 * Whether cached data should be fetched again.
	 *
	 * @param age        time since the last fetch started or failed
	 * @param failures   failed fetches in a row; 0 if the data loaded
	 * @param maxAge     how old loaded data can get before it may have changed
	 * @param userAction whether the user opened the window, switched the timeframe or hovered the button
	 */
	private static boolean isDue(long age, int failures, long maxAge, boolean userAction)
	{
		if (failures == 0)
		{
			// Loaded: only a user action refreshes it, and only once it may have changed.
			return userAction && age >= maxAge;
		}
		if (userAction)
		{
			// Failed: the user can always try again, after a short pause.
			return age >= RETRY_MS;
		}
		// Failed: a few automatic retries, further apart each time.
		return failures <= MAX_RETRIES && age >= retryDelay(failures);
	}

	/** 5s, 10s, 20s after the 1st, 2nd and 3rd failure in a row. */
	private static long retryDelay(int failures)
	{
		return RETRY_MS << (failures - 1);
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
