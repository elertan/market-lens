package com.marketlens.ui;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.game.SpriteOverride;

/**
 * Custom sprites, registered with RuneLite's SpriteManager so widgets can use them like game sprites.
 * IDs are negative so they never collide with sprites from the game cache.
 */
@Getter
@RequiredArgsConstructor
public enum MarketLensSprite implements SpriteOverride
{
	/** Green and red price lines, for the GE button. All icons are made by tools/make_icons.py. */
	CHART_ICON(-5401, "chart_icon.png", 20, 18),
	/** Smaller variant for the window's title bar. */
	CHART_ICON_SMALL(-5403, "chart_icon_small.png", 12, 12),
	/** Four outward corners, for the button that expands the chart. */
	EXPAND_ICON(-5404, "expand_icon.png", 13, 13),
	/** Chart type toggle: line chart. */
	LINE_ICON(-5405, "line_icon.png", 13, 13),
	/** Chart type toggle: candlesticks. */
	CANDLE_ICON(-5406, "candle_icon.png", 13, 13);

	private final int spriteId;
	private final String fileName;
	private final int width;
	private final int height;
}
