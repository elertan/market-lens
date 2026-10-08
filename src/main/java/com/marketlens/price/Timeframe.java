package com.marketlens.price;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import static com.marketlens.price.Timeframe.Seconds.DAY;
import static com.marketlens.price.Timeframe.Seconds.HOUR;
import static com.marketlens.price.Timeframe.Seconds.MINUTES_5;

/**
 * Chart timeframes. Each maps to a wiki API "lookback". The API has no 1h lookback,
 * so 1H loads the 6h series and opens zoomed in on the last hour.
 * A series only gains a point once per bucket, so it is never refreshed more often than that.
 */
@Getter
@RequiredArgsConstructor
public enum Timeframe
{
	ONE_HOUR("1H", "6h", 3_600L, MINUTES_5),
	SIX_HOURS("6H", "6h", 0L, MINUTES_5),
	ONE_DAY("24H", "24h", 0L, MINUTES_5),
	ONE_WEEK("1W", "7d", 0L, HOUR),
	ONE_MONTH("1M", "30d", 0L, 6 * HOUR),
	SIX_MONTHS("6M", "6m", 0L, DAY),
	ONE_YEAR("1Y", "1y", 0L, DAY);

	private final String label;
	private final String lookback;
	/** Initial visible window in seconds, or 0 to show the full series. */
	private final long initialWindowSeconds;
	/** Bucket size of the series the API returns for this lookback, in seconds. */
	private final long bucketSeconds;

	@Override
	public String toString()
	{
		return label;
	}

	static final class Seconds
	{
		static final long MINUTES_5 = 300;
		static final long HOUR = 3_600;
		static final long DAY = 86_400;

		private Seconds()
		{
		}
	}
}
