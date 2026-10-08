package com.marketlens.price;

import lombok.Value;

/** One entry from /latest. Any field may be null when the item has not traded recently. */
@Value
public class LatestPrice
{
	Long high;
	Long highTime;
	Long low;
	Long lowTime;
}
