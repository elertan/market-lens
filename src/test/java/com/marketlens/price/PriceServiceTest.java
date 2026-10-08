package com.marketlens.price;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import okhttp3.OkHttpClient;
import org.junit.Before;
import org.junit.Test;

/** Checks how often the service asks the wiki for data. */
public class PriceServiceTest
{
	private static final int ITEM = 4151;

	private long now = TimeUnit.DAYS.toMillis(20_000);
	private FakeClient client;
	private PriceService service;

	@Before
	public void setUp()
	{
		client = new FakeClient();
		service = new PriceService(client, () -> now);
	}

	@Test
	public void livePriceIsFetchedAtMostOncePerMinute()
	{
		service.refresh(ITEM, Timeframe.ONE_DAY);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(1, client.count("latest"));

		now += TimeUnit.SECONDS.toMillis(61);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(2, client.count("latest"));
	}

	@Test
	public void failedLivePriceRetriesAfterFiveSeconds()
	{
		service.refresh(ITEM, Timeframe.ONE_DAY);
		client.failLatest();
		now += TimeUnit.SECONDS.toMillis(4);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(1, client.count("latest"));

		now += TimeUnit.SECONDS.toMillis(2);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(2, client.count("latest"));
	}

	@Test
	public void seriesIsRefreshedOncePerBucket()
	{
		service.getSeries(ITEM, Timeframe.ONE_YEAR);
		client.succeedSeries();
		now += TimeUnit.HOURS.toMillis(12);
		service.getSeries(ITEM, Timeframe.ONE_YEAR);
		assertEquals("daily buckets: no refetch within a day", 1, client.count("timeseries:1y"));

		now += TimeUnit.HOURS.toMillis(13);
		service.getSeries(ITEM, Timeframe.ONE_YEAR);
		assertEquals(2, client.count("timeseries:1y"));
	}

	@Test
	public void failedSeriesRetriesAfterFiveSeconds()
	{
		service.getSeries(ITEM, Timeframe.ONE_DAY);
		client.failSeries();
		now += TimeUnit.SECONDS.toMillis(4);
		service.getSeries(ITEM, Timeframe.ONE_DAY);
		assertEquals(1, client.count("timeseries:24h"));

		now += TimeUnit.SECONDS.toMillis(2);
		service.getSeries(ITEM, Timeframe.ONE_DAY);
		assertEquals(2, client.count("timeseries:24h"));
	}

	@Test
	public void mappingRetriesWithGrowingDelays()
	{
		service.refresh(ITEM, Timeframe.ONE_DAY);
		client.failMapping();
		// Retried after 5s, then 10s, not on every call in between.
		now += TimeUnit.SECONDS.toMillis(4);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(1, client.count("mapping"));

		now += TimeUnit.SECONDS.toMillis(2);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(2, client.count("mapping"));
		client.failMapping();

		now += TimeUnit.SECONDS.toMillis(9);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(2, client.count("mapping"));

		now += TimeUnit.SECONDS.toMillis(2);
		service.refresh(ITEM, Timeframe.ONE_DAY);
		assertEquals(3, client.count("mapping"));
	}

	@Test
	public void hourVolumeSumsTheLastHourOfFiveMinuteBuckets()
	{
		assertNull(service.getHourVolume(ITEM));

		service.refresh(ITEM, Timeframe.ONE_DAY);
		long nowSeconds = TimeUnit.MILLISECONDS.toSeconds(now);
		client.succeed("timeseries:6h", Arrays.asList(
			point(nowSeconds - 7_200, 100, 100),  // two hours ago: not counted
			point(nowSeconds - 3_000, 5, 7),
			point(nowSeconds - 300, 1, null)));

		assertEquals(Long.valueOf(13), service.getHourVolume(ITEM));
	}

	private static TimeseriesPoint point(long timestamp, Integer highVolume, Integer lowVolume)
	{
		return new TimeseriesPoint(timestamp, 100.0, 90.0,
			highVolume == null ? null : highVolume.longValue(), lowVolume == null ? null : lowVolume.longValue());
	}

	/** Records requests instead of sending them, and lets the test answer them. */
	private static class FakeClient extends WikiPriceClient
	{
		private final List<String> requests = new ArrayList<>();
		private final List<Runnable> mappingFailures = new ArrayList<>();
		private final List<Runnable> latestFailures = new ArrayList<>();
		private final Map<String, Consumer<List<TimeseriesPoint>>> seriesSuccess = new HashMap<>();
		private final List<Runnable> seriesFailures = new ArrayList<>();

		FakeClient()
		{
			super(new OkHttpClient(), new Gson());
		}

		@Override
		public void fetchLatest(int itemId, Consumer<LatestPrice> onSuccess, Runnable onFailure)
		{
			requests.add("latest");
			latestFailures.add(onFailure);
		}

		@Override
		public void fetchMapping(Consumer<Map<Integer, ItemMapping>> onSuccess, Runnable onFailure)
		{
			requests.add("mapping");
			mappingFailures.add(onFailure);
		}

		@Override
		public void fetchTimeseries(int itemId, Timeframe timeframe, Consumer<List<TimeseriesPoint>> onSuccess,
			Runnable onFailure)
		{
			String key = "timeseries:" + timeframe.getLookback();
			requests.add(key);
			seriesSuccess.put(key, onSuccess);
			seriesFailures.add(onFailure);
		}

		int count(String request)
		{
			return Collections.frequency(requests, request);
		}

		void failLatest()
		{
			latestFailures.remove(latestFailures.size() - 1).run();
		}

		void failMapping()
		{
			mappingFailures.remove(mappingFailures.size() - 1).run();
		}

		void succeedSeries()
		{
			seriesSuccess.values().forEach(c -> c.accept(Collections.emptyList()));
		}

		void succeed(String key, List<TimeseriesPoint> points)
		{
			seriesSuccess.get(key).accept(points);
		}

		void failSeries()
		{
			seriesFailures.forEach(Runnable::run);
		}
	}
}
