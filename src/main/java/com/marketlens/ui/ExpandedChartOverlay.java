package com.marketlens.ui;

import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayLayer;

/** The chart in the expanded window, which sits above all interfaces; still drawn under the right-click menu. */
@Singleton
public class ExpandedChartOverlay extends ChartOverlay
{
	private final ExpandedChartWindow window;

	@Inject
	ExpandedChartOverlay(ChartRenderer renderer, ExpandedChartWindow window)
	{
		super(renderer);
		this.window = window;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	Rectangle chartBounds()
	{
		return window.getChartBounds();
	}
}
