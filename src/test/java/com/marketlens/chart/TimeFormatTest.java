package com.marketlens.chart;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Locale;
import org.junit.Test;

public class TimeFormatTest
{
	@Test
	public void localeClock()
	{
		assertTrue(TimeFormat.usesTwelveHour(Locale.US));
		assertFalse(TimeFormat.usesTwelveHour(Locale.UK));
		assertFalse(TimeFormat.usesTwelveHour(Locale.forLanguageTag("nl-NL")));
	}

	@Test
	public void explicitChoice()
	{
		assertTrue(TimeFormat.TWELVE_HOUR.isTwelveHour());
		assertFalse(TimeFormat.TWENTY_FOUR_HOUR.isTwelveHour());
	}
}
