package com.marketlens.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.SpritePixels;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;

/** Small helpers for building dynamic widgets. Client thread only. */
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

	/**
	 * Copies the look of {@code source} (its sprites, texts and rectangles, flattened) into {@code parent}
	 * at x,y and stretches it to w x h like a 9-slice: parts wider/taller than half the source stretch,
	 * parts in the right/bottom half keep their distance to that edge, the rest keep their position.
	 * Listeners are not copied. Returns the new widgets in draw order.
	 */
	public List<Widget> copyLook(Widget source, Widget parent, int x, int y, int w, int h)
	{
		List<Widget> parts = new ArrayList<>();
		collectVisibleParts(source, parts);

		Rectangle src = source.getBounds();
		List<Widget> copies = new ArrayList<>(parts.size());
		for (Widget part : parts)
		{
			Rectangle b = part.getBounds();
			int[] hz = stretch(b.x - src.x, b.width, src.width, w);
			int[] vt = stretch(b.y - src.y, b.height, src.height, h);

			Widget copy = place(parent.createChild(part.getType()), x + hz[0], y + vt[0], hz[1], vt[1]);
			copy.setSpriteId(part.getSpriteId());
			copy.setSpriteTiling(part.getSpriteTiling());
			copy.setFlippedHorizontally(part.isFlippedHorizontally());
			copy.setFlippedVertically(part.isFlippedVertically());
			copy.setOpacity(part.getOpacity());
			copy.setFilled(part.isFilled());
			copy.setTextColor(part.getTextColor());
			copy.setText(part.getText());
			copy.setFontId(part.getFontId());
			copy.setTextShadowed(part.getTextShadowed());
			copy.setXTextAlignment(part.getXTextAlignment());
			copy.setYTextAlignment(part.getYTextAlignment());
			copies.add(copy);
		}
		return copies;
	}

	private static void collectVisibleParts(Widget widget, List<Widget> out)
	{
		if (widget == null || widget.isSelfHidden())
		{
			return;
		}
		int type = widget.getType();
		if (type == WidgetType.GRAPHIC || type == WidgetType.TEXT || type == WidgetType.RECTANGLE)
		{
			out.add(widget);
		}
		// Same order the client draws them in.
		for (Widget[] children : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()})
		{
			if (children != null)
			{
				for (Widget child : children)
				{
					collectVisibleParts(child, out);
				}
			}
		}
	}

	/** Returns {offset, size} of a part after stretching its container from {@code from} to {@code to}. */
	private static int[] stretch(int offset, int size, int from, int to)
	{
		if (size > from / 2)
		{
			return new int[]{offset, size + to - from};
		}
		if (offset + size / 2 > from / 2)
		{
			return new int[]{offset + to - from, size};
		}
		return new int[]{offset, size};
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
