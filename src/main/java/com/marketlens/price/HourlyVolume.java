package com.marketlens.price;

import lombok.Value;

/** One entry from /1h. Only the volume fields are used. */
@Value
public class HourlyVolume
{
	Long highPriceVolume;
	Long lowPriceVolume;

	public long total()
	{
		return (highPriceVolume == null ? 0 : highPriceVolume) + (lowPriceVolume == null ? 0 : lowPriceVolume);
	}
}
