package com.marketlens.price;

import lombok.Value;

/** One bucket from /timeseries. Prices are null when nothing traded on that side in the bucket. */
@Value
public class TimeseriesPoint
{
	long timestamp;
	Double avgHighPrice;
	Double avgLowPrice;
	Long highPriceVolume;
	Long lowPriceVolume;

	public long totalVolume()
	{
		return (highPriceVolume == null ? 0 : highPriceVolume) + (lowPriceVolume == null ? 0 : lowPriceVolume);
	}
}
