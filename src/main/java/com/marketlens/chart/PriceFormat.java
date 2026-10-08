package com.marketlens.chart;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Number and age formatting shared by the window and the chart. */
public final class PriceFormat
{
	private static final DecimalFormatSymbols SYMBOLS = DecimalFormatSymbols.getInstance(Locale.ENGLISH);

	private PriceFormat()
	{
	}

	/** 1712400 -> "1,712,400". */
	public static String exact(long value)
	{
		return new DecimalFormat("#,##0", SYMBOLS).format(value);
	}

	/** Signed variant for deltas: 14400 -> "+14,400", -34248 -> "-34,248". */
	public static String signed(long value)
	{
		return (value > 0 ? "+" : "") + exact(value);
	}

	/** Short form for axis labels: 950 -> "950", 12_500 -> "12.5K", 1_712_400 -> "1.71M", 2.1e9 -> "2.1B". */
	public static String compact(double value)
	{
		double abs = Math.abs(value);
		if (abs >= 1e9)
		{
			return scaled(value / 1e9) + "B";
		}
		if (abs >= 1e6)
		{
			return scaled(value / 1e6) + "M";
		}
		if (abs >= 1e4)
		{
			return scaled(value / 1e3) + "K";
		}
		return new DecimalFormat("#,##0", SYMBOLS).format(value);
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
		return String.format(Locale.ENGLISH, "%." + decimals + "f%s", value / unit, unit == 1e9 ? "B" : "M");
	}

	private static String scaled(double v)
	{
		double abs = Math.abs(v);
		String pattern = abs >= 100 ? "0" : abs >= 10 ? "0.#" : "0.##";
		return new DecimalFormat(pattern, SYMBOLS).format(v);
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
}
