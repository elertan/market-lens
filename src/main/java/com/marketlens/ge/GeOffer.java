package com.marketlens.ge;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/** The offer being set up in the GE. Client thread only. */
@Singleton
public class GeOffer
{
	/**
	 * Price per item of the offer being set up. Not named in RuneLite's gameval constants yet; identified by
	 * watching it follow the GE's -5%/+5% buttons (137 -> 143 -> 150 -> 143).
	 */
	private static final int PRICE_VARP = 5753;

	private final Client client;
	private final ItemManager itemManager;

	@Inject
	GeOffer(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	/** The price per item chosen for {@code itemId} on the GE's offer setup screen, or null if none is being set up. */
	public Long priceFor(int itemId)
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		if (setup == null || setup.isHidden())
		{
			return null;
		}
		int searched = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (searched <= 0 || itemManager.canonicalize(searched) != itemId)
		{
			return null;
		}
		int price = client.getVarpValue(PRICE_VARP);
		return price > 0 ? (long) price : null;
	}
}
