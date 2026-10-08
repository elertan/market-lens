package com.marketlens.chart;

import com.marketlens.price.TimeseriesPoint;
import lombok.Value;

/** One candlestick, built from the wiki's bucket averages (see {@link Candles}). */
@Value
public class Candle
{
	long timestamp;
	double open;
	double high;
	double low;
	double close;
	Long buyVolume;
	Long sellVolume;

	public boolean isUp()
	{
		return close >= open;
	}

	/** The candle as a chart point (high, low and volumes), so the scale and volume bars can use it unchanged. */
	public TimeseriesPoint asPoint()
	{
		return new TimeseriesPoint(timestamp, high, low, buyVolume, sellVolume);
	}
}
