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
	/** Green and red price lines; made by tools/make_chart_icon.py. */
	CHART_ICON(-5401, "chart_icon.png", 26, 24);

	private final int spriteId;
	private final String fileName;
	private final int width;
	private final int height;
}
