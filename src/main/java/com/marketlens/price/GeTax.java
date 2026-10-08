package com.marketlens.price;

import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.runelite.api.gameval.ItemID;

/**
 * Grand Exchange convenience fee, as of the 29 May 2025 update:
 * 2% of the sale price per item, rounded down, capped at 5M, with a list of exempt items.
 */
public final class GeTax
{
	private static final long RATE_PERCENT = 2;
	private static final long CAP = 5_000_000L;

	private static final Set<Integer> EXEMPT = ImmutableSet.of(
		ItemID.OSRS_BOND,
		ItemID._4DOSE1ENERGY, ItemID._3DOSE1ENERGY, ItemID._2DOSE1ENERGY, ItemID._1DOSE1ENERGY,
		ItemID.BRONZE_ARROW, ItemID.IRON_ARROW, ItemID.STEEL_ARROW,
		ItemID.BRONZE_DART, ItemID.IRON_DART, ItemID.STEEL_DART,
		ItemID.MINDRUNE,
		ItemID.BASS, ItemID.BREAD, ItemID.CAKE, ItemID.COOKED_CHICKEN, ItemID.COOKED_MEAT, ItemID.HERRING,
		ItemID.LOBSTER, ItemID.MACKEREL, ItemID.MEAT_PIE, ItemID.PIKE, ItemID.SALMON, ItemID.SHRIMP, ItemID.TUNA,
		ItemID.POH_TABLET_ARDOUGNETELEPORT, ItemID.POH_TABLET_CAMELOTTELEPORT, ItemID.POH_TABLET_FORTISTELEPORT,
		ItemID.POH_TABLET_FALADORTELEPORT, ItemID.POH_TABLET_KOURENDTELEPORT, ItemID.POH_TABLET_LUMBRIDGETELEPORT,
		ItemID.POH_TABLET_VARROCKTELEPORT, ItemID.POH_TABLET_TELEPORTTOHOUSE,
		ItemID.NECKLACE_OF_MINIGAMES_8, ItemID.RING_OF_DUELING_8,
		ItemID.CHISEL, ItemID.GARDENING_TROWEL, ItemID.GLASSBLOWINGPIPE, ItemID.HAMMER, ItemID.NEEDLE,
		ItemID.PESTLE_AND_MORTAR, ItemID.RAKE, ItemID.POH_SAW, ItemID.SECATEURS, ItemID.DIBBER, ItemID.SHEARS,
		ItemID.SPADE,
		ItemID.WATERING_CAN_0, ItemID.WATERING_CAN_1, ItemID.WATERING_CAN_2, ItemID.WATERING_CAN_3,
		ItemID.WATERING_CAN_4, ItemID.WATERING_CAN_5, ItemID.WATERING_CAN_6, ItemID.WATERING_CAN_7,
		ItemID.WATERING_CAN_8
	);

	private GeTax()
	{
	}

	/** Tax paid by the seller when one unit of {@code itemId} sells for {@code price}. */
	public static long of(int itemId, long price)
	{
		if (EXEMPT.contains(itemId))
		{
			return 0;
		}
		// Integer maths so 2% of e.g. 350 is exactly 7, not 6.999...
		return Math.min(CAP, price * RATE_PERCENT / 100);
	}
}
