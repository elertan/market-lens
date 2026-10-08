package com.marketlens.chart;

import com.marketlens.price.TimeseriesPoint;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds candlesticks from the wiki's price buckets. The API has no open/close or single-trade highs and lows,
 * only each bucket's average instant-buy and instant-sell price, so a candle is built from those averages:
 * <ul>
 * <li>open: the previous candle's close (the first bucket's midpoint for the first candle)</li>
 * <li>close: the midpoint of the candle's last bucket that traded</li>
 * <li>high: the highest average instant-buy price in the candle; low: the lowest average instant-sell price
 * (widened to include open and close)</li>
 * </ul>
 * Buckets are grouped by fixed clock boundaries of {@code intervalSeconds}, like {@link Downsample}.
 */
public final class Candles
{
	private Candles()
	{
	}

	public static List<Candle> build(List<TimeseriesPoint> points, long intervalSeconds)
	{
		List<Candle> candles = new ArrayList<>();
		Double previousClose = null;
		Group group = null;
		for (TimeseriesPoint point : points)
		{
			long start = Math.floorDiv(point.getTimestamp(), intervalSeconds) * intervalSeconds;
			if (group == null || group.start != start)
			{
				previousClose = finish(group, previousClose, candles);
				group = new Group(start);
			}
			group.add(point);
		}
		finish(group, previousClose, candles);
		return candles;
	}

	/** Adds the group's candle, if it traded at all, and returns the close to open the next candle with. */
	private static Double finish(Group group, Double previousClose, List<Candle> candles)
	{
		if (group == null || group.lastMid == null)
		{
			return previousClose;
		}
		double open = previousClose != null ? previousClose : group.firstMid;
		double close = group.lastMid;
		double high = Math.max(Math.max(open, close), group.high);
		double low = Math.min(Math.min(open, close), group.low);
		candles.add(new Candle(group.start, open, high, low, close, group.buyVolume, group.sellVolume));
		return close;
	}

	private static final class Group
	{
		private final long start;
		private Double firstMid;
		private Double lastMid;
		private double high = -Double.MAX_VALUE;
		private double low = Double.MAX_VALUE;
		private Long buyVolume;
		private Long sellVolume;

		Group(long start)
		{
			this.start = start;
		}

		void add(TimeseriesPoint p)
		{
			Double buy = p.getAvgHighPrice();
			Double sell = p.getAvgLowPrice();
			if (buy != null)
			{
				high = Math.max(high, buy);
				low = Math.min(low, buy);
			}
			if (sell != null)
			{
				high = Math.max(high, sell);
				low = Math.min(low, sell);
			}
			Double mid = buy;
			if (buy != null && sell != null)
			{
				mid = (buy + sell) / 2;
			}
			else if (buy == null)
			{
				mid = sell;
			}
			if (mid != null)
			{
				if (firstMid == null)
				{
					firstMid = mid;
				}
				lastMid = mid;
			}
			buyVolume = add(buyVolume, p.getHighPriceVolume());
			sellVolume = add(sellVolume, p.getLowPriceVolume());
		}

		private static Long add(Long total, Long volume)
		{
			return volume == null ? total : (total == null ? 0 : total) + volume;
		}
	}
}
