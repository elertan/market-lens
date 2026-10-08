package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ChartViewportTest
{
	private static final double EPS = 1e-6;

	@Test
	public void resetShowsFullRangeByDefault()
	{
		ChartViewport v = new ChartViewport();
		v.setData(0, 1000, 50, 0);
		assertEquals(0, v.getStart(), EPS);
		assertEquals(1000, v.getEnd(), EPS);
	}

	@Test
	public void resetHonoursInitialWindowAnchoredToEnd()
	{
		ChartViewport v = new ChartViewport();
		v.setData(0, 21_600, 900, 3_600);
		assertEquals(18_000, v.getStart(), EPS);
		assertEquals(21_600, v.getEnd(), EPS);
	}

	@Test
	public void zoomKeepsAnchorFixed()
	{
		ChartViewport v = new ChartViewport();
		v.setData(0, 1000, 10, 0);
		v.zoom(0.5, 250);
		// anchor was at 25% of the window and stays there
		assertEquals(125, v.getStart(), EPS);
		assertEquals(625, v.getEnd(), EPS);
	}

	@Test
	public void zoomIsClampedToMinSpanAndDataRange()
	{
		ChartViewport v = new ChartViewport();
		v.setData(0, 1000, 100, 0);
		v.zoom(0.01, 500);
		assertEquals(100, v.getEnd() - v.getStart(), EPS);
		v.zoom(1000, 500);
		assertEquals(0, v.getStart(), EPS);
		assertEquals(1000, v.getEnd(), EPS);
	}

	@Test
	public void panStopsAtDataEdges()
	{
		ChartViewport v = new ChartViewport();
		v.setData(0, 1000, 10, 200);
		v.pan(500);
		assertEquals(800, v.getStart(), EPS);
		v.pan(-5000);
		assertEquals(0, v.getStart(), EPS);
		assertEquals(200, v.getEnd(), EPS);
	}
}
