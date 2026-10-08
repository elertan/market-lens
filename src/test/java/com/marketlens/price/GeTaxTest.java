package com.marketlens.price;

import static org.junit.Assert.assertEquals;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class GeTaxTest
{
	private static final int WHIP = ItemID.ABYSSAL_WHIP;

	@Test
	public void twoPercentRoundedDown()
	{
		assertEquals(16_577, GeTax.of(WHIP, 828_861));
		assertEquals(7, GeTax.of(WHIP, 350));
		assertEquals(1, GeTax.of(WHIP, 50));
	}

	@Test
	public void belowFiftyIsFree()
	{
		assertEquals(0, GeTax.of(WHIP, 49));
	}

	@Test
	public void cappedAtFiveMillion()
	{
		assertEquals(5_000_000, GeTax.of(WHIP, 2_000_000_000L));
	}

	@Test
	public void exemptItemsPayNothing()
	{
		assertEquals(0, GeTax.of(ItemID.OSRS_BOND, 10_000_000));
	}
}
