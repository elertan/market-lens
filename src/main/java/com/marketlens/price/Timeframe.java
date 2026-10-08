package com.marketlens.price;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Chart timeframes. Each maps to a wiki API "lookback". The API has no 1h lookback,
 * so 1H loads the 6h series and opens zoomed in on the last hour.
 */
@Getter
@RequiredArgsConstructor
public enum Timeframe
{
	ONE_HOUR("1H", "6h", 3_600L),
	SIX_HOURS("6H", "6h", 0L),
	ONE_DAY("24H", "24h", 0L),
	ONE_WEEK("1W", "7d", 0L),
	ONE_MONTH("1M", "30d", 0L),
	SIX_MONTHS("6M", "6m", 0L),
	ONE_YEAR("1Y", "1y", 0L);

	private final String label;
	private final String lookback;
	/** Initial visible window in seconds, or 0 to show the full series. */
	private final long initialWindowSeconds;

	@Override
	public String toString()
	{
		return label;
	}
}
