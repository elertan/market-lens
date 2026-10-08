package com.marketlens.ge;

import com.marketlens.ui.WidgetFactory;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.IntConsumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.game.ItemManager;

/**
 * Keeps a "Market Lens" button on the GE offer setup screen, right of the Confirm button,
 * mirrored to the back arrow on the left. It copies the Confirm button's look at a smaller size.
 * The game rebuilds the setup widgets often, so {@link #update()} re-adds the button whenever it disappears.
 * Client thread only.
 */
@Slf4j
@Singleton
public class GeButtonInjector
{
	private static final String LABEL = "Market Lens";
	private static final double WIDTH_SCALE = 0.7;
	private static final double HEIGHT_SCALE = 0.8;
	private static final int MIN_GAP = 6;

	private final Client client;
	private final ItemManager itemManager;
	private final WidgetFactory widgets;

	private Widget button;
	private int shownItemId = -1;
	private IntConsumer onClick = id -> {};
	private IntConsumer onItemShown = id -> {};

	@Inject
	GeButtonInjector(Client client, ItemManager itemManager, WidgetFactory widgets)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.widgets = widgets;
	}

	public void setOnClick(IntConsumer onClick)
	{
		this.onClick = onClick;
	}

	/** Called once per item while its setup screen is shown; lets the plugin prefetch prices before the click. */
	public void setOnItemShown(IntConsumer onItemShown)
	{
		this.onItemShown = onItemShown;
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
			shownItemId = -1;
			return;
		}

		if (itemId != shownItemId)
		{
			shownItemId = itemId;
			onItemShown.accept(itemManager.canonicalize(itemId));
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
		shownItemId = -1;
	}

	private Widget createButton(Widget parent, Widget confirm)
	{
		Rectangle c = confirm.getBounds();
		int confirmRight = c.x + c.width;
		int confirmCenterX = c.x + c.width / 2;

		// Mirror the back arrow's centre across the Confirm button's centre.
		Widget back = client.getWidget(InterfaceID.GeOffers.BACK);
		int centerX = back != null && !back.isHidden()
			? 2 * confirmCenterX - (back.getBounds().x + back.getBounds().width / 2)
			: confirmRight + c.width / 2;

		int w = (int) Math.min(c.width * WIDTH_SCALE, 2 * (centerX - confirmRight - MIN_GAP));
		int h = (int) (c.height * HEIGHT_SCALE);
		Point origin = parent.getCanvasLocation();
		int x = centerX - w / 2 - origin.getX();
		int y = c.y + (c.height - h) / 2 - origin.getY();

		Widget layer = widgets.layer(parent, x, y, w, h);
		List<Widget> parts = widgets.copyLook(confirm, layer, 0, 0, w, h);

		boolean hasLabel = false;
		for (Widget part : parts)
		{
			if (part.getType() == WidgetType.TEXT)
			{
				part.setText(LABEL);
				part.setFontId(FontID.BOLD_12);
				hasLabel = true;
			}
		}
		if (!hasLabel)
		{
			widgets.text(layer, LABEL, FontID.BOLD_12, 0xFFFFFF, WidgetTextAlignment.CENTER, 0, 0, w, h);
		}

		layer.setName("<col=ff9040>" + LABEL + "</col>");
		layer.setAction(0, "View prices");
		layer.setHasListener(true);
		layer.setOnOpListener((JavaScriptCallback) e -> onClick.accept(currentItem()));
		layer.setOnMouseOverListener((JavaScriptCallback) e -> swapSprites(parts, SpriteID.GeIcons.BUTTON, SpriteID.GeIcons.BUTTON_HOVERED));
		layer.setOnMouseLeaveListener((JavaScriptCallback) e -> swapSprites(parts, SpriteID.GeIcons.BUTTON_HOVERED, SpriteID.GeIcons.BUTTON));
		log.debug("Added Market Lens button at {},{} ({}x{}), copied {} parts from Confirm", x, y, w, h, parts.size());
		return layer;
	}

	/** Hover effect, used when the copied Confirm button is built from the GE button sprite. */
	private static void swapSprites(List<Widget> parts, int from, int to)
	{
		for (Widget part : parts)
		{
			if (part.getSpriteId() == from)
			{
				part.setSpriteId(to);
			}
		}
	}

	private int currentItem()
	{
		return itemManager.canonicalize(client.getVarpValue(VarPlayerID.TRADINGPOST_SEARCH));
	}
}
