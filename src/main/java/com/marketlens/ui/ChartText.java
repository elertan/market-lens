package com.marketlens.ui;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

/** Text drawing for the chart, in the OSRS style, with labels centred on the real pixel height of digits. */
final class ChartText
{
	/** Vertical padding inside axis label boxes, top and bottom combined. */
	private static final int LABEL_PADDING = 6;

	private final Graphics2D g;
	private final Rectangle2D digitBounds;

	/** @param digitBounds visual bounds of "0123456789" in the current font, relative to the baseline */
	ChartText(Graphics2D g, Rectangle2D digitBounds)
	{
		this.g = g;
		this.digitBounds = digitBounds;
	}

	/** OSRS-style text: one-pixel black drop shadow. */
	void draw(String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	void drawCentered(String text, int centerX, int y, Color color)
	{
		draw(text, centerX - width(text) / 2, y, color);
	}

	/** A filled label box of {@code width} on an axis, with text vertically centred on {@code centerY}. */
	void drawBox(String text, int x, int centerY, int width, Color background, Color foreground)
	{
		int h = labelHeight();
		g.setColor(background);
		g.fillRect(x, centerY - h / 2, width, h);
		g.setColor(foreground);
		g.drawString(text, x + 3, baselineFor(centerY));
	}

	int width(String text)
	{
		return metrics().stringWidth(text);
	}

	int ascent()
	{
		return metrics().getAscent();
	}

	int labelHeight()
	{
		return (int) Math.ceil(digitBounds.getHeight()) + LABEL_PADDING;
	}

	/** Baseline that puts digits visually centred on {@code centerY}. */
	int baselineFor(int centerY)
	{
		return centerY - (int) Math.round(digitBounds.getY() + digitBounds.getHeight() / 2);
	}

	private FontMetrics metrics()
	{
		return g.getFontMetrics();
	}
}
