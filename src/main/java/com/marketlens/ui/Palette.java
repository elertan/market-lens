package com.marketlens.ui;

import java.awt.Color;

/** Colours shared by the widget window (as RGB ints) and the chart overlay (as {@link Color}s). */
public final class Palette
{
	/** OSRS interface title / label orange. */
	public static final int ORANGE = 0xFF981F;
	public static final int WHITE = 0xFFFFFF;
	public static final int YELLOW = 0xFFFF00;
	public static final int MUTED = 0x9F9F9F;
	/** Default buy/sell colours: the average of the green and red shades in the Market Lens icon. */
	public static final int BUY = 0x63B936;
	public static final int SELL = 0xD05A3D;
	/** The GE's own colour for the price-per-item field; used for the chosen offer price on the chart. */
	public static final int OFFER = 0xFFB83F;
	public static final int DIVIDER = 0x5A5245;
	public static final int CHART_BACKGROUND = 0x000000;

	public static final Color OFFER_COLOR = new Color(OFFER);
	public static final Color OFFER_TAG_LINE = new Color(0xFF, 0xB8, 0x3F, 170);
	public static final Color GRID = new Color(255, 255, 255, 22);
	public static final Color AXIS_TEXT = new Color(0xC8B48C);
	public static final Color CROSSHAIR = new Color(255, 255, 255, 120);
	public static final Color LABEL_BACKGROUND = new Color(0x2B2620);
	public static final Color TEXT = new Color(WHITE);
	public static final Color TEXT_ORANGE = new Color(ORANGE);
	public static final Color TEXT_MUTED = new Color(MUTED);

	private Palette()
	{
	}
}
