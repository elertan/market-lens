package com.marketlens.chart;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class PriceFormatTest
{
	@Test
	public void exactAndSigned()
	{
		assertEquals("1,712,400", PriceFormat.exact(1_712_400));
		assertEquals("+14,400", PriceFormat.signed(14_400));
		assertEquals("-34,248", PriceFormat.signed(-34_248));
		assertEquals("0", PriceFormat.signed(0));
	}

	@Test
	public void axisUsesEnoughDecimalsForTheStep()
	{
		assertEquals("812,500", PriceFormat.axis(812_500, 2_500));
		assertEquals("1.705M", PriceFormat.axis(1_705_000, 5_000));
		assertEquals("1.71M", PriceFormat.axis(1_710_000, 10_000));
		assertEquals("2M", PriceFormat.axis(2_000_000, 1_000_000));
		assertEquals("1.25B", PriceFormat.axis(1_250_000_000, 50_000_000));
	}

	@Test
	public void age()
	{
		assertEquals("just now", PriceFormat.age(30));
		assertEquals("4m ago", PriceFormat.age(250));
		assertEquals("3h ago", PriceFormat.age(3 * 3600 + 5));
		assertEquals("2d ago", PriceFormat.age(2 * 86_400));
	}
}
