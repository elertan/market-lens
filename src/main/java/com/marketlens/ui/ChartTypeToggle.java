package com.marketlens.ui;

import com.marketlens.chart.ChartType;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.api.widgets.Widget;

/** Two small icon buttons, line and candles, that switch the chart type; the active one is highlighted. */
class ChartTypeToggle
{
	static final int BUTTON_WIDTH = 22;
	static final int BUTTON_HEIGHT = 24;
	private static final int GAP = 2;
	static final int WIDTH = 2 * BUTTON_WIDTH + GAP;

	private final Map<ChartType, IconButton> buttons = new EnumMap<>(ChartType.class);

	/** Builds the pair with its top-left at x,y in {@code parent}. */
	ChartTypeToggle(GeWidgets geWidgets, Widget parent, int x, int y, ChartType selected, Consumer<ChartType> onSelect)
	{
		add(geWidgets, parent, ChartType.LINE, MarketLensSprite.LINE_ICON, x, y, onSelect);
		add(geWidgets, parent, ChartType.CANDLES, MarketLensSprite.CANDLE_ICON, x + BUTTON_WIDTH + GAP, y, onSelect);
		select(selected);
	}

	void select(ChartType type)
	{
		buttons.forEach((t, button) -> button.setSelected(t == type));
	}

	/** The chart type of the button at canvas point x,y, or null. */
	ChartType at(int x, int y)
	{
		for (Map.Entry<ChartType, IconButton> button : buttons.entrySet())
		{
			if (button.getValue().getWidget().getBounds().contains(x, y))
			{
				return button.getKey();
			}
		}
		return null;
	}

	private void add(GeWidgets geWidgets, Widget parent, ChartType type, MarketLensSprite icon, int x, int y,
		Consumer<ChartType> onSelect)
	{
		buttons.put(type, geWidgets.iconButton(parent, icon, type.getLabel() + " chart", "Show", x, y,
			BUTTON_WIDTH, BUTTON_HEIGHT, () -> onSelect.accept(type), () -> {}));
	}
}
