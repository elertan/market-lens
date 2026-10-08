package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.marketlens.price.TimeseriesPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class DownsampleTest
{
	private static final double EPS = 1e-9;

	@Test
	public void keepsRawBucketsWhenTheyAreFarEnoughApart()
	{
		// 5-minute buckets at 60 seconds per pixel are 5 px apart
		assertEquals(300, Downsample.interval(300, 60, 3));
	}

	@Test
	public void picksTheSmallestNiceIntervalThatFits()
	{
		// 24h of 5-minute buckets in ~370 px: 233 s/px; 15 min = 3.9 px
		assertEquals(900, Downsample.interval(300, 86_400.0 / 370, 3));
		// a week of hourly buckets in ~370 px: 2 hours = 4.4 px
		assertEquals(7_200, Downsample.interval(3_600, 7 * 86_400.0 / 370, 3));
		// a year of daily buckets in ~370 px: 3 days = 3 px
		assertEquals(3 * 86_400, Downsample.interval(86_400, 365 * 86_400.0 / 370, 3));
	}

	@Test
	public void onlyUsesWholeMultiplesOfTheBucket()
	{
		// 6-hour buckets 1 px apart: 12 hours (2 px) is too dense, so 1 day (4 px); never e.g. 3 hours
		assertEquals(86_400, Downsample.interval(21_600, 21_600, 3));
	}

	@Test
	public void mergedPriceIsTheVolumeWeightedAverage()
	{
		List<TimeseriesPoint> merged = Downsample.merge(Arrays.asList(
			new TimeseriesPoint(0, 100.0, 90.0, 10L, 1L),
			new TimeseriesPoint(300, 104.0, 94.0, 30L, 3L)), 900);

		assertEquals(1, merged.size());
		TimeseriesPoint p = merged.get(0);
		assertEquals("10 trades at 100 and 30 at 104", 103.0, p.getAvgHighPrice(), EPS);
		assertEquals(93.0, p.getAvgLowPrice(), EPS);
		assertEquals(Long.valueOf(40), p.getHighPriceVolume());
		assertEquals(Long.valueOf(4), p.getLowPriceVolume());
	}

	@Test
	public void bucketsWithoutTradesDontCount()
	{
		List<TimeseriesPoint> merged = Downsample.merge(Arrays.asList(
			new TimeseriesPoint(0, null, 90.0, 0L, 2L),
			new TimeseriesPoint(300, 104.0, null, 5L, 0L),
			new TimeseriesPoint(600, null, null, 0L, 0L)), 900);

		TimeseriesPoint p = merged.get(0);
		assertEquals(104.0, p.getAvgHighPrice(), EPS);
		assertEquals(90.0, p.getAvgLowPrice(), EPS);
		assertEquals(Long.valueOf(5), p.getHighPriceVolume());
	}

	@Test
	public void sideWithNoPricesStaysEmpty()
	{
		List<TimeseriesPoint> merged = Downsample.merge(Arrays.asList(
			new TimeseriesPoint(0, null, 90.0, null, 2L),
			new TimeseriesPoint(300, null, 91.0, null, 2L)), 900);

		assertNull(merged.get(0).getAvgHighPrice());
		assertNull(merged.get(0).getHighPriceVolume());
	}

	@Test
	public void groupsAlignToClockBoundaries()
	{
		// Buckets at 10:45, 10:50 | 11:00, 11:05 with 15-minute groups -> starts 10:45 and 11:00
		long t = 10 * 3600 + 45 * 60;
		List<TimeseriesPoint> merged = Downsample.merge(Arrays.asList(
			new TimeseriesPoint(t, 1.0, 1.0, 1L, 1L),
			new TimeseriesPoint(t + 300, 1.0, 1.0, 1L, 1L),
			new TimeseriesPoint(t + 900, 1.0, 1.0, 1L, 1L),
			new TimeseriesPoint(t + 1200, 1.0, 1.0, 1L, 1L)), 900);

		assertEquals(2, merged.size());
		assertEquals(t, merged.get(0).getTimestamp());
		assertEquals(t + 900, merged.get(1).getTimestamp());
	}
}
