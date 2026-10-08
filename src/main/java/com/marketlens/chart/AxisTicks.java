package com.marketlens.chart;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** "Nice" tick positions and labels for the price and time axes. */
public final class AxisTicks
{
	private static final long MINUTE = 60;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;
	private static final long[] TIME_STEPS = {
		5 * MINUTE, 10 * MINUTE, 15 * MINUTE, 30 * MINUTE,
		HOUR, 2 * HOUR, 3 * HOUR, 6 * HOUR, 12 * HOUR,
		DAY, 2 * DAY, 7 * DAY, 14 * DAY, 30 * DAY, 61 * DAY, 91 * DAY, 182 * DAY,
	};

	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);
	private static final DateTimeFormatter MONTH_YEAR = DateTimeFormatter.ofPattern("MMM ''yy", Locale.ENGLISH);
	private static final DateTimeFormatter CROSSHAIR_INTRADAY = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter CROSSHAIR_DAILY = DateTimeFormatter.ofPattern("EEE d MMM ''yy", Locale.ENGLISH);

	private AxisTicks()
	{
	}

	/** Round price levels (1, 2 or 5 x 10^n, at least 1gp apart) inside [min, max]. */
	public static List<Double> prices(double min, double max, int targetCount)
	{
		List<Double> ticks = new ArrayList<>();
		if (!(max > min) || targetCount < 1)
		{
			return ticks;
		}

		double raw = (max - min) / targetCount;
		double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
		double norm = raw / magnitude;
		double nice = norm < 1.5 ? 1 : norm < 3 ? 2 : norm < 7 ? 5 : 10;
		double step = Math.max(1, nice * magnitude);

		double first = Math.ceil(min / step) * step;
		for (int i = 0; first + i * step <= max; i++)
		{
			ticks.add(first + i * step);
		}
		return ticks;
	}

	/** Smallest step from {@link #TIME_STEPS} that yields at most {@code targetCount} ticks. */
	public static long timeStep(double start, double end, int targetCount)
	{
		double span = end - start;
		for (long step : TIME_STEPS)
		{
			if (span / step <= targetCount)
			{
				return step;
			}
		}
		return TIME_STEPS[TIME_STEPS.length - 1];
	}

	/** Tick times aligned to local wall-clock multiples of {@code step}. */
	public static List<Long> times(double start, double end, long step, ZoneId zone)
	{
		List<Long> ticks = new ArrayList<>();
		if (!(end > start))
		{
			return ticks;
		}
		long offset = zone.getRules().getOffset(Instant.ofEpochSecond((long) start)).getTotalSeconds();
		long first = (long) Math.ceil((start + offset) / step) * step - offset;
		for (long t = first; t <= end; t += step)
		{
			ticks.add(t);
		}
		return ticks;
	}

	/** Axis label: clock time for intraday steps (date at midnight), day-month or month-year otherwise. */
	public static String timeLabel(long epochSeconds, long step, ZoneId zone)
	{
		ZonedDateTime t = Instant.ofEpochSecond(epochSeconds).atZone(zone);
		if (step < DAY)
		{
			boolean midnight = t.getHour() == 0 && t.getMinute() == 0;
			return (midnight ? DAY_MONTH : CLOCK).format(t);
		}
		return (step < 30 * DAY ? DAY_MONTH : MONTH_YEAR).format(t);
	}

	/** Full timestamp for the crosshair label. */
	public static String crosshairLabel(long epochSeconds, long bucketSeconds, ZoneId zone)
	{
		ZonedDateTime t = Instant.ofEpochSecond(epochSeconds).atZone(zone);
		return (bucketSeconds < DAY ? CROSSHAIR_INTRADAY : CROSSHAIR_DAILY).format(t);
	}
}
