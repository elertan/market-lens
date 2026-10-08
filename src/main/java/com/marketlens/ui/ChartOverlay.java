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
		// Widgets the client hasn't drawn yet report position -1,-1; drawing then would flash the chart in the
		// top-left corner for a frame. A real chart area lies inside its window, so never at negative coordinates.
		if (area != null && area.x >= 0 && area.y >= 0)
		{
			renderer.render(graphics, area);
		}
		return null;
	}
}
