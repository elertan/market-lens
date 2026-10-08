package com.marketlens.chart;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Number and age formatting shared by the window and the chart. The chart formats labels every frame,
 * so formatters are created once per thread (DecimalFormat isn't thread-safe) and reused.
 */
public final class PriceFormat
{
	private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ENGLISH);
	private static final ThreadLocal<DecimalFormat> GROUPED = ThreadLocal.withInitial(() -> format("#,##0"));
	/** Fixed-decimal formats for scaled axis labels, indexed by number of decimals. */
	private static final ThreadLocal<DecimalFormat[]> SCALED = ThreadLocal.withInitial(() -> new DecimalFormat[]{
		format("0"), format("0.0"), format("0.00"), format("0.000"),
	});

	private PriceFormat()
	{
	}

	/** 1712400 -> "1,712,400". */
	public static String exact(long value)
	{
		return GROUPED.get().format(value);
	}

	/** Signed variant for deltas: 14400 -> "+14,400", -34248 -> "-34,248". */
	public static String signed(long value)
	{
		return (value > 0 ? "+" : "") + exact(value);
	}

	/**
	 * Axis label with just enough decimals to tell ticks {@code step} apart:
	 * below 1M it is exact ("812,500"), above it is scaled ("1.705M" for a 5K step, "1.71M" for a 10K step).
	 */
	public static String axis(double value, double step)
	{
		double abs = Math.abs(value);
		if (abs < 1e6)
		{
			return exact(Math.round(value));
		}
		double unit = abs >= 1e9 ? 1e9 : 1e6;
		int decimals = (int) Math.max(0, Math.min(3, Math.ceil(-Math.log10(step / unit))));
		return SCALED.get()[decimals].format(value / unit) + (unit == 1e9 ? "B" : "M");
	}

	/** Seconds elapsed -> "just now", "4m ago", "3h ago", "2d ago". */
	public static String age(long seconds)
	{
		if (seconds < 60)
		{
			return "just now";
		}
		if (seconds < 3600)
		{
			return (seconds / 60) + "m ago";
		}
		if (seconds < 86400)
		{
			return (seconds / 3600) + "h ago";
		}
		return (seconds / 86400) + "d ago";
	}

	private static DecimalFormat format(String pattern)
	{
		DecimalFormat format = new DecimalFormat(pattern, SYMBOLS);
		format.setRoundingMode(RoundingMode.HALF_UP);
		return format;
	}
}
