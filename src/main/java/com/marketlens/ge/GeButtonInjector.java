package com.marketlens.ge;

import com.marketlens.ui.GeWidgets;
import com.marketlens.ui.MarketLensSprite;
import java.awt.Rectangle;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;

/**
 * Keeps a Market Lens button on the GE offer setup screen, right of the Confirm button,
 * mirrored to the back arrow on the left: an icon-only small GE button with a price-chart icon.
 * The game rebuilds the setup widgets often, so {@link #update()} re-adds the button whenever it disappears.
 * Client thread only.
 */
@Slf4j
@Singleton
public class GeButtonInjector
{
	private static final String NAME = "Market Lens";
	private static final int MIN_GAP = 6;

	private final Client client;
	private final ItemManager itemManager;
	private final GeWidgets geWidgets;

	private Widget button;
	private IntConsumer onClick = id -> {};
	private IntConsumer onHover = id -> {};

	@Inject
	GeButtonInjector(Client client, ItemManager itemManager, GeWidgets geWidgets)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.geWidgets = geWidgets;
	}

	public void setOnClick(IntConsumer onClick)
	{
		this.onClick = onClick;
	}

	/** Called when the mouse moves onto the button; lets the plugin start loading prices before the click. */
	public void setOnHover(IntConsumer onHover)
	{
		this.onHover = onHover;
	}

	/** Cheap enough to run every client tick: does nothing while the button is in place. */
	public void update()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		Widget confirm = client.getWidget(InterfaceID.GeOffers.SETUP_CONFIRM);
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (setup == null || setup.isHidden() || confirm == null || confirm.isHidden() || itemId <= 0)
		{
			hide();
			return;
		}

		Widget parent = confirm.getParent();
		if (button != null && parent.getChild(button.getIndex()) == button)
		{
			button.setHidden(false);
			return;
		}
		button = createButton(parent, confirm);
	}

	public void hide()
	{
		if (button != null)
		{
			button.setHidden(true);
		}
	}

	public void reset()
	{
		hide();
		button = null;
	}

	private Widget createButton(Widget parent, Widget confirm)
	{
		Rectangle c = confirm.getBounds();
		int confirmCenterX = c.x + c.width / 2;

		// Mirror the back arrow across the Confirm button: the right margin equals the arrow's left margin.
		Widget back = client.getWidget(InterfaceID.GeOffers.BACK);
		int right = back != null && !back.isHidden()
			? 2 * confirmCenterX - back.getBounds().x
			: c.x + c.width + GeWidgets.SMALL_BUTTON_SIZE + MIN_GAP;
		int left = Math.max(right - GeWidgets.SMALL_BUTTON_SIZE, c.x + c.width + MIN_GAP);

		Point origin = parent.getCanvasLocation();
		int x = left - origin.getX();
		int y = c.y + (c.height - GeWidgets.SMALL_BUTTON_SIZE) / 2 - origin.getY();

		log.debug("Adding Market Lens button at {},{}", x, y);
		return geWidgets.iconButton(parent, MarketLensSprite.CHART_ICON, NAME, "View prices", x, y,
			GeWidgets.SMALL_BUTTON_SIZE, GeWidgets.SMALL_BUTTON_SIZE,
			() -> onClick.accept(currentItem()), () -> onHover.accept(currentItem()));
	}

	private int currentItem()
	{
		return itemManager.canonicalize(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH));
	}
}
