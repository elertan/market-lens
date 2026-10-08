package com.marketlens.ui;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the chart into the chart area of one of the windows, if that window is showing. */
abstract class ChartOverlay extends Overlay
{
	private final ChartRenderer renderer;

	ChartOverlay(ChartRenderer renderer)
	{
		this.renderer = renderer;
		setPosition(OverlayPosition.DYNAMIC);
	}

	/** Canvas bounds of the window's chart area, or null when that window isn't showing. */
	abstract Rectangle chartBounds();

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Rectangle area = chartBounds();
		if (area != null)
		{
			renderer.render(graphics, area);
		}
		return null;
	}
}
