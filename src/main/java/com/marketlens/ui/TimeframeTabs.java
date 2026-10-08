package com.marketlens.ui;

import com.marketlens.price.Timeframe;
import java.awt.Dimension;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.FontID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;

/** A row of timeframe buttons on GE text-box sprites; the selected one is white. Client thread only. */
class TimeframeTabs
{
	private static final int TAB_WIDTH = 30;
	private static final int TAB_GAP = 2;
	private static final Dimension FALLBACK_CAP = new Dimension(4, 20);

	private final Map<Timeframe, Widget> labels = new EnumMap<>(Timeframe.class);
	private Timeframe selected;

	/** Builds the row with its top-left at x,y in {@code parent}. */
	TimeframeTabs(WidgetFactory widgets, Widget parent, int x, int y, Timeframe selected, Consumer<Timeframe> onSelect)
	{
		this.selected = selected;
		Dimension left = widgets.spriteSize(SpriteID.GeTextbackdrop.LEFT, FALLBACK_CAP);
		Dimension right = widgets.spriteSize(SpriteID.GeTextbackdrop.RIGHT, FALLBACK_CAP);
		int h = height(widgets);

		for (Timeframe timeframe : Timeframe.values())
		{
			widgets.sprite(parent, SpriteID.GeTextbackdrop.LEFT, x, y, left.width, h);
			widgets.tiledSprite(parent, SpriteID.GeTextbackdrop.MIDDLE, x + left.width, y, TAB_WIDTH - left.width - right.width, h);
			widgets.sprite(parent, SpriteID.GeTextbackdrop.RIGHT, x + TAB_WIDTH - right.width, y, right.width, h);

			Widget label = widgets.text(parent, timeframe.getLabel(), FontID.PLAIN_11, colour(timeframe),
				WidgetTextAlignment.CENTER, x, y, TAB_WIDTH, h);
			label.setName("<col=ff9040>" + timeframe.getLabel() + "</col>");
			label.setAction(0, "View");
			label.setHasListener(true);
			label.setOnOpListener((JavaScriptCallback) e -> onSelect.accept(timeframe));
			label.setOnMouseOverListener((JavaScriptCallback) e -> label.setTextColor(Palette.YELLOW));
			label.setOnMouseLeaveListener((JavaScriptCallback) e -> label.setTextColor(colour(timeframe)));
			labels.put(timeframe, label);

			x += TAB_WIDTH + TAB_GAP;
		}
	}

	static int height(WidgetFactory widgets)
	{
		return widgets.spriteSize(SpriteID.GeTextbackdrop.LEFT, FALLBACK_CAP).height;
	}

	/** The tab at canvas point x,y, or null. */
	Timeframe at(int x, int y)
	{
		for (Map.Entry<Timeframe, Widget> tab : labels.entrySet())
		{
			if (tab.getValue().getBounds().contains(x, y))
			{
				return tab.getKey();
			}
		}
		return null;
	}

	void select(Timeframe timeframe)
	{
		selected = timeframe;
		labels.forEach((tf, label) -> label.setTextColor(colour(tf)));
	}

	private int colour(Timeframe timeframe)
	{
		return timeframe == selected ? Palette.WHITE : Palette.ORANGE;
	}
}
