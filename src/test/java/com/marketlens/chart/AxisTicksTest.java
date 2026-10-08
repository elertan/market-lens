package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class AxisTicksTest
{
	private static final ZoneId UTC = ZoneOffset.UTC;

	@Test
	public void priceTicksAreRound()
	{
		assertEquals(Arrays.asList(805_000.0, 810_000.0, 815_000.0, 820_000.0, 825_000.0),
			AxisTicks.prices(801_554, 828_861, 5));
	}

	@Test
	public void priceTicksUseQuarterStepsWhenTheyFitBetter()
	{
		assertEquals(Arrays.asList(1_250.0, 1_500.0, 1_750.0, 2_000.0, 2_250.0, 2_500.0),
			AxisTicks.prices(1_034, 2_620, 6));
	}

	@Test
	public void priceTicksNeverStepBelowOneCoin()
	{
		List<Double> ticks = AxisTicks.prices(10, 12, 10);
		assertEquals(Arrays.asList(10.0, 11.0, 12.0), ticks);
	}

	@Test
	public void timeStepPicksSmallestFittingStep()
	{
		assertEquals(3 * 3600, AxisTicks.timeStep(0, 86_400, 8));
		assertEquals(15 * 60, AxisTicks.timeStep(0, 3_600, 5));
	}

	@Test
	public void timeTicksAlignToWallClock()
	{
		// 00:10 .. 03:20 UTC, hourly ticks land on the hour
		assertEquals(Arrays.asList(3_600L, 7_200L, 10_800L), AxisTicks.times(600, 12_000, 3_600, UTC));
	}

	@Test
	public void timeLabels()
	{
		assertEquals("01:00", AxisTicks.timeLabel(3_600, 3_600, UTC, false));
		assertEquals("2 Jan", AxisTicks.timeLabel(86_400, 3_600, UTC, false));
		assertEquals("2 Jan", AxisTicks.timeLabel(86_400, 86_400, UTC, false));
		assertEquals("Jan '70", AxisTicks.timeLabel(86_400, 30 * 86_400, UTC, false));
	}

	@Test
	public void twelveHourLabels()
	{
		assertEquals("1:00 PM", AxisTicks.timeLabel(13 * 3_600, 3_600, UTC, true));
		assertEquals("2 Jan", AxisTicks.timeLabel(86_400, 3_600, UTC, true));
		assertEquals("Thu 1 Jan 1:00 PM", AxisTicks.crosshairLabel(13 * 3_600, 300, UTC, true));
		assertEquals("Thu 1 Jan 13:00", AxisTicks.crosshairLabel(13 * 3_600, 300, UTC, false));
	}
}
