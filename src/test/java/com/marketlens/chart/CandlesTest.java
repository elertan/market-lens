package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.marketlens.price.TimeseriesPoint;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class CandlesTest
{
	private static final double EPS = 1e-9;

	private static TimeseriesPoint point(long t, Double buy, Double sell)
	{
		return new TimeseriesPoint(t, buy, sell, 1L, 2L);
	}

	@Test
	public void candleSpansHighestBuyToLowestSellAndClosesAtLastMidpoint()
	{
		List<Candle> candles = Candles.build(Arrays.asList(
			point(0, 104.0, 100.0),     // mid 102: opens the first candle
			point(300, 110.0, 101.0),
			point(600, 107.0, 98.0)),   // mid 102.5: closes it
			900);

		assertEquals(1, candles.size());
		Candle c = candles.get(0);
		assertEquals(102.0, c.getOpen(), EPS);
		assertEquals(110.0, c.getHigh(), EPS);
		assertEquals(98.0, c.getLow(), EPS);
		assertEquals(102.5, c.getClose(), EPS);
		assertEquals(Long.valueOf(3), c.getBuyVolume());
		assertEquals(Long.valueOf(6), c.getSellVolume());
		assertTrue(c.isUp());
	}

	@Test
	public void nextCandleOpensAtThePreviousClose()
	{
		List<Candle> candles = Candles.build(Arrays.asList(
			point(0, 104.0, 100.0),
			point(300, 100.0, 96.0)), 300);

		assertEquals(2, candles.size());
		assertEquals(102.0, candles.get(1).getOpen(), EPS);
		assertEquals(98.0, candles.get(1).getClose(), EPS);
		assertFalse(candles.get(1).isUp());
	}

	@Test
	public void wicksAlwaysIncludeOpenAndClose()
	{
		// Only a sell price in the second bucket: its mid (90) is below its own range of buys (none)
		List<Candle> candles = Candles.build(Arrays.asList(
			point(0, 104.0, 100.0),
			point(300, null, 90.0)), 300);

		Candle second = candles.get(1);
		assertEquals(102.0, second.getHigh(), EPS);
		assertEquals(90.0, second.getLow(), EPS);
	}

	@Test
	public void bucketsWithoutTradesAreSkipped()
	{
		List<Candle> candles = Candles.build(Arrays.asList(
			point(0, 104.0, 100.0),
			point(300, null, null),
			point(600, 106.0, 102.0)), 300);

		assertEquals(2, candles.size());
		assertEquals(600, candles.get(1).getTimestamp());
		assertEquals(102.0, candles.get(1).getOpen(), EPS);
	}
}
