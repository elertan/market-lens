package com.marketlens.chart;

import com.marketlens.price.TimeseriesPoint;
import java.awt.Rectangle;
import java.util.List;
import lombok.Getter;

/**
 * Geometry of one chart frame: which points are visible, the price range, the price and volume panes,
 * and the mapping between time/price and pixels. Pure maths, so it can be tested without a client.
 */
public final class ChartScale
{
	private static final double VOLUME_FRACTION = 0.2;
	private static final int PANE_GAP = 4;
	/** Room above and below the highest and lowest price, as a fraction of the range. */
	private static final double PRICE_PADDING = 0.08;

	private final List<TimeseriesPoint> points;
	private final double start;
	private final double end;
	private final long bucketSeconds;
	@Getter
	private final Rectangle plot;
	@Getter
	private final Rectangle pricePane;
	@Getter
	private final Rectangle volumePane;
	/** First and last visible point, with one point of slack either side so lines run to the edges. */
	@Getter
	private final int first;
	@Getter
	private final int last;
	@Getter
	private final double min;
	@Getter
	private final double max;
	@Getter
	private final long maxVolume;

	/**
	 * @param points     the series, oldest first; not empty
	 * @param start      visible window start, epoch seconds
	 * @param end        visible window end, epoch seconds
	 * @param livePrices current prices to fit on the scale when the newest point is visible (nulls ignored)
	 */
	public ChartScale(List<TimeseriesPoint> points, double start, double end, long bucketSeconds, Rectangle plot,
		Double... livePrices)
	{
		this.points = points;
		this.start = start;
		this.end = end;
		this.bucketSeconds = bucketSeconds;
		this.plot = plot;

		int volumeHeight = (int) (plot.height * VOLUME_FRACTION);
		pricePane = new Rectangle(plot.x, plot.y, plot.width, plot.height - volumeHeight - PANE_GAP);
		volumePane = new Rectangle(plot.x, plot.y + plot.height - volumeHeight, plot.width, volumeHeight);

		int lo = 0;
		while (lo < points.size() - 1 && points.get(lo + 1).getTimestamp() < start)
		{
			lo++;
		}
		int hi = points.size() - 1;
		while (hi > lo && points.get(hi - 1).getTimestamp() > end)
		{
			hi--;
		}
		first = lo;
		last = hi;

		Range range = new Range();
		long volume = 1;
		for (int i = first; i <= last; i++)
		{
			TimeseriesPoint p = points.get(i);
			range.include(p.getAvgHighPrice());
			range.include(p.getAvgLowPrice());
			volume = Math.max(volume, p.totalVolume());
		}
		// When the newest data is in view, fit the live prices too, so their tags never fall off the scale.
		if (last == points.size() - 1)
		{
			for (Double price : livePrices)
			{
				range.include(price);
			}
		}
		if (range.min > range.max)
		{
			range.min = 0;
			range.max = 1;
		}
		double pad = Math.max(1, (range.max - range.min) * PRICE_PADDING);
		min = range.min - pad;
		max = range.max + pad;
		maxVolume = volume;
	}

	public int x(double time)
	{
		return plot.x + (int) Math.round((time - start) / (end - start) * plot.width);
	}

	public int y(double price)
	{
		return pricePane.y + (int) Math.round((max - price) / (max - min) * pricePane.height);
	}

	public double timeAt(int x)
	{
		return start + (double) (x - plot.x) / plot.width * (end - start);
	}

	public double priceAt(int y)
	{
		return max - (double) (y - pricePane.y) / pricePane.height * (max - min);
	}

	public double getStart()
	{
		return start;
	}

	public double getEnd()
	{
		return end;
	}

	/** Width of one bucket in pixels. */
	public double bucketWidth()
	{
		return plot.width * bucketSeconds / (end - start);
	}

	/** Bar height in the volume pane for {@code volume}, scaled to the largest visible volume. */
	public int volumeHeight(Long volume)
	{
		return volume == null ? 0 : (int) (volume * volumePane.height / maxVolume);
	}

	/** The visible point closest to {@code time}. */
	public TimeseriesPoint nearest(double time)
	{
		TimeseriesPoint best = points.get(first);
		for (int i = first + 1; i <= last; i++)
		{
			TimeseriesPoint p = points.get(i);
			if (Math.abs(p.getTimestamp() - time) < Math.abs(best.getTimestamp() - time))
			{
				best = p;
			}
		}
		return best;
	}

	private static final class Range
	{
		private double min = Double.MAX_VALUE;
		private double max = -Double.MAX_VALUE;

		void include(Double price)
		{
			if (price != null)
			{
				min = Math.min(min, price);
				max = Math.max(max, price);
			}
		}
	}
}
