package com.marketlens.chart;

import com.marketlens.price.TimeseriesPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Merges price buckets into larger ones when they are too dense to read, without losing accuracy.
 * The wiki's bucket price is the average of the trades in that bucket, so the volume-weighted average of
 * several buckets is exactly the average price of all their trades together. Volumes are summed.
 * Groups are aligned to fixed clock boundaries, so they don't shift while the user pans.
 */
public final class Downsample
{
	private static final long MINUTE = 60;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;
	/** Intervals a series can be merged into, smallest first. */
	private static final long[] INTERVALS = {
		5 * MINUTE, 15 * MINUTE, 30 * MINUTE, HOUR, 2 * HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR,
		DAY, 2 * DAY, 3 * DAY, 7 * DAY, 14 * DAY, 28 * DAY,
	};

	private Downsample()
	{
	}

	/**
	 * The smallest interval, a whole multiple of {@code bucketSeconds}, at which points are at least
	 * {@code minPixels} apart. Returns {@code bucketSeconds} itself when the raw buckets are far enough apart.
	 */
	public static long interval(long bucketSeconds, double secondsPerPixel, double minPixels)
	{
		if (bucketSeconds / secondsPerPixel >= minPixels)
		{
			return bucketSeconds;
		}
		for (long interval : INTERVALS)
		{
			if (interval > bucketSeconds && interval % bucketSeconds == 0 && interval / secondsPerPixel >= minPixels)
			{
				return interval;
			}
		}
		return INTERVALS[INTERVALS.length - 1];
	}

	/** Merges {@code points} (oldest first) into buckets of {@code intervalSeconds}, aligned to multiples of it. */
	public static List<TimeseriesPoint> merge(List<TimeseriesPoint> points, long intervalSeconds)
	{
		List<TimeseriesPoint> merged = new ArrayList<>();
		Group group = null;
		for (TimeseriesPoint point : points)
		{
			long start = Math.floorDiv(point.getTimestamp(), intervalSeconds) * intervalSeconds;
			if (group == null || group.start != start)
			{
				if (group != null)
				{
					merged.add(group.toPoint());
				}
				group = new Group(start);
			}
			group.add(point);
		}
		if (group != null)
		{
			merged.add(group.toPoint());
		}
		return merged;
	}

	/** Running totals for one merged bucket. */
	private static final class Group
	{
		private final long start;
		private final Side high = new Side();
		private final Side low = new Side();

		Group(long start)
		{
			this.start = start;
		}

		void add(TimeseriesPoint point)
		{
			high.add(point.getAvgHighPrice(), point.getHighPriceVolume());
			low.add(point.getAvgLowPrice(), point.getLowPriceVolume());
		}

		TimeseriesPoint toPoint()
		{
			return new TimeseriesPoint(start, high.price(), low.price(), high.volume(), low.volume());
		}
	}

	/** One side (instant buys or instant sells) of a merged bucket. */
	private static final class Side
	{
		private double weightedSum;
		private long weight;
		private double plainSum;
		private int prices;
		private Long volume;

		void add(Double price, Long tradeVolume)
		{
			if (tradeVolume != null)
			{
				volume = (volume == null ? 0 : volume) + tradeVolume;
			}
			if (price == null)
			{
				return;
			}
			plainSum += price;
			prices++;
			if (tradeVolume != null && tradeVolume > 0)
			{
				weightedSum += price * tradeVolume;
				weight += tradeVolume;
			}
		}

		/** Volume-weighted average; a plain average only if no bucket reported its volume. */
		Double price()
		{
			if (weight > 0)
			{
				return weightedSum / weight;
			}
			return prices > 0 ? plainSum / prices : null;
		}

		Long volume()
		{
			return volume;
		}
	}
}
