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
	public void compact()
	{
		assertEquals("950", PriceFormat.compact(950));
		assertEquals("9,999", PriceFormat.compact(9_999));
		assertEquals("12.5K", PriceFormat.compact(12_500));
		assertEquals("829K", PriceFormat.compact(828_861));
		assertEquals("1.71M", PriceFormat.compact(1_712_400));
		assertEquals("2.1B", PriceFormat.compact(2_100_000_000.0));
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
