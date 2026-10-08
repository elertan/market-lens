package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import com.marketlens.price.TimeseriesPoint;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class ChartScaleTest
{
	private static final Rectangle PLOT = new Rectangle(0, 0, 1000, 500);
	private static final double EPS = 1e-6;

	/** Points every 100s from t=0, with high = 100 + i and low = 90 + i. */
	private static List<TimeseriesPoint> series(int count)
	{
		List<TimeseriesPoint> points = new ArrayList<>();
		for (int i = 0; i < count; i++)
		{
			points.add(new TimeseriesPoint(i * 100L, 100.0 + i, 90.0 + i, 1L, 1L));
		}
		return points;
	}

	@Test
	public void visibleSliceKeepsOnePointOfSlackEachSide()
	{
		ChartScale scale = new ChartScale(series(10), 250, 550, 100, PLOT, true);
		assertEquals(2, scale.getFirst());
		assertEquals(6, scale.getLast());
	}

	@Test
	public void priceRangeIsPaddedByEightPercent()
	{
		// Visible points 2..6: lows from 92, highs up to 106 -> range 14, padding 1.12
		ChartScale scale = new ChartScale(series(10), 250, 550, 100, PLOT, true);
		assertEquals(92 - 1.12, scale.getMin(), EPS);
		assertEquals(106 + 1.12, scale.getMax(), EPS);
	}

	@Test
	public void livePricesAreFittedOnlyWhenTheNewestPointIsVisible()
	{
		ChartScale atEnd = new ChartScale(series(10), 500, 900, 100, PLOT, true, 150.0, null);
		assertEquals(150.0, atEnd.getMax() - (150 - 94) * 0.08, EPS);

		ChartScale inPast = new ChartScale(series(10), 0, 300, 100, PLOT, true, 150.0, null);
		assertEquals(104 + 14 * 0.08, inPast.getMax(), EPS);
	}

	@Test
	public void pixelMappingRoundTrips()
	{
		ChartScale scale = new ChartScale(series(10), 0, 900, 100, PLOT, true);
		assertEquals(0, scale.x(0));
		assertEquals(1000, scale.x(900));
		assertEquals(450, scale.timeAt(scale.x(450)), 1);
		assertEquals(100, scale.priceAt(scale.y(100)), 0.5);
	}

	@Test
	public void nearestPicksTheClosestVisiblePoint()
	{
		ChartScale scale = new ChartScale(series(10), 0, 900, 100, PLOT, true);
		assertEquals(300, scale.nearest(340).getTimestamp());
		assertEquals(400, scale.nearest(360).getTimestamp());
	}
}
