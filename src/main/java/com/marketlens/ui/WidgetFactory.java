package com.marketlens.ui;

import java.awt.Dimension;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/** Primitive builders for dynamic widgets. Client thread only. */
@Singleton
public class WidgetFactory
{
	private final Client client;

	@Inject
	WidgetFactory(Client client)
	{
		this.client = client;
	}

	/** Native size of a cache sprite, or {@code fallback} if it is not loaded. */
	public Dimension spriteSize(int spriteId, Dimension fallback)
	{
		SpritePixels[] sprites = client.getSprites(client.getIndexSprites(), spriteId, 0);
		if (sprites == null || sprites.length == 0 || sprites[0] == null)
		{
			return fallback;
		}
		return new Dimension(sprites[0].getMaxWidth(), sprites[0].getMaxHeight());
	}

	public Widget layer(Widget parent, int x, int y, int w, int h)
	{
		return place(parent.createChild(WidgetType.LAYER), x, y, w, h);
	}

	public Widget sprite(Widget parent, int spriteId, int x, int y, int w, int h)
	{
		Widget widget = place(parent.createChild(WidgetType.GRAPHIC), x, y, w, h);
		widget.setSpriteId(spriteId);
		return widget;
	}

	public Widget tiledSprite(Widget parent, int spriteId, int x, int y, int w, int h)
	{
		Widget widget = sprite(parent, spriteId, x, y, w, h);
		widget.setSpriteTiling(true);
		return widget;
	}

	public Widget rect(Widget parent, int color, int opacity, boolean filled, int x, int y, int w, int h)
	{
		Widget widget = place(parent.createChild(WidgetType.RECTANGLE), x, y, w, h);
		widget.setTextColor(color);
		widget.setOpacity(opacity);
		widget.setFilled(filled);
		return widget;
	}

	public Widget text(Widget parent, String text, int fontId, int color, int xAlign, int x, int y, int w, int h)
	{
		Widget widget = place(parent.createChild(WidgetType.TEXT), x, y, w, h);
		widget.setText(text);
		widget.setFontId(fontId);
		widget.setTextColor(color);
		widget.setTextShadowed(true);
		widget.setXTextAlignment(xAlign);
		widget.setYTextAlignment(WidgetTextAlignment.CENTER);
		return widget;
	}

	/** Positions a new widget relative to its parent. Parents must be placed before their children. */
	private static Widget place(Widget widget, int x, int y, int w, int h)
	{
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setOriginalWidth(w);
		widget.setOriginalHeight(h);
		widget.revalidate();
		return widget;
	}
}
