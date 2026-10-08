package com.marketlens.ge;

import com.marketlens.ui.WidgetFactory;
import java.awt.Dimension;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemManager;

/**
 * Keeps a small magnifier icon next to the item on the GE offer setup screen.
 * The game rebuilds the setup widgets often, so {@link #update()} re-adds the icon whenever it disappears.
 * Client thread only.
 */
@Slf4j
@Singleton
public class GeIconInjector
{
	private static final int ICON_SPRITE = SpriteID.GeSmallicons.SEARCH;
	private static final int OPACITY_IDLE = 60;
	private static final int OPACITY_HOVER = 0;

	private final Client client;
	private final ItemManager itemManager;
	private final WidgetFactory widgets;

	private Widget icon;
	private IntConsumer onClick = id -> {};

	@Inject
	GeIconInjector(Client client, ItemManager itemManager, WidgetFactory widgets)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.widgets = widgets;
	}

	public void setOnClick(IntConsumer onClick)
	{
		this.onClick = onClick;
	}

	/** Cheap enough to run every client tick: does nothing while the icon is in place. */
	public void update()
	{
		Widget setup = client.getWidget(InterfaceID.GeOffers.SETUP);
		int itemId = client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH);
		if (setup == null || setup.isHidden() || itemId <= 0)
		{
			hide();
			return;
		}

		if (icon != null && setup.getChild(icon.getIndex()) == icon)
		{
			icon.setHidden(false);
			return;
		}

		Widget item = findItemWidget(setup, itemId);
		if (item == null)
		{
			return;
		}
		icon = createIcon(setup, item);
	}

	public void hide()
	{
		if (icon != null)
		{
			icon.setHidden(true);
		}
	}

	public void reset()
	{
		hide();
		icon = null;
	}

	private static Widget findItemWidget(Widget setup, int itemId)
	{
		Widget[] children = setup.getDynamicChildren();
		Widget anyItem = null;
		for (Widget child : children)
		{
			if (child == null || child.getType() != WidgetType.GRAPHIC || child.getItemId() <= 0)
			{
				continue;
			}
			if (child.getItemId() == itemId)
			{
				return child;
			}
			anyItem = child;
		}
		// The setup screen may show the noted or placeholder variant of the searched item.
		return anyItem;
	}

	private Widget createIcon(Widget setup, Widget item)
	{
		Dimension size = widgets.spriteSize(ICON_SPRITE, new Dimension(16, 16));
		// Badge on the item's top-right corner, overlapping slightly like the game's own item badges.
		int x = item.getRelativeX() + item.getWidth() - size.width + 4;
		int y = item.getRelativeY() - 4;

		Widget w = widgets.sprite(setup, ICON_SPRITE, x, y, size.width, size.height);
		w.setOpacity(OPACITY_IDLE);
		w.setName("<col=ff9040>Market Lens</col>");
		w.setAction(0, "View prices");
		w.setHasListener(true);
		w.setOnOpListener((JavaScriptCallback) e -> onClick.accept(currentItem()));
		w.setOnMouseOverListener((JavaScriptCallback) e -> w.setOpacity(OPACITY_HOVER));
		w.setOnMouseLeaveListener((JavaScriptCallback) e -> w.setOpacity(OPACITY_IDLE));
		log.debug("Added price icon at {},{} next to item widget {}", x, y, item.getItemId());
		return w;
	}

	private int currentItem()
	{
		return itemManager.canonicalize(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH));
	}
}
