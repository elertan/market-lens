package com.marketlens.chart;

import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 12- or 24-hour clock for the chart's time labels. */
@Getter
@RequiredArgsConstructor
public enum TimeFormat
{
	AUTOMATIC("Automatic"),
	TWELVE_HOUR("12-hour"),
	TWENTY_FOUR_HOUR("24-hour");

	/** Whether the default locale writes times with a 12-hour clock; looked up once. */
	private static final boolean LOCALE_TWELVE_HOUR = usesTwelveHour(Locale.getDefault(Locale.Category.FORMAT));

	private final String label;

	public boolean isTwelveHour()
	{
		return this == AUTOMATIC ? LOCALE_TWELVE_HOUR : this == TWELVE_HOUR;
	}

	/** True if the locale's short time pattern uses a 12-hour field, like "h:mm a" in the US. */
	static boolean usesTwelveHour(Locale locale)
	{
		String pattern = DateTimeFormatterBuilder.getLocalizedDateTimePattern(null, FormatStyle.SHORT,
			IsoChronology.INSTANCE, locale);
		return pattern.indexOf('h') >= 0 || pattern.indexOf('K') >= 0;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
