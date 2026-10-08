package com.marketlens.ui;

import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.overlay.OverlayLayer;

/** The chart in the normal window. Drawn right after the GE interface so game menus still render on top. */
@Singleton
public class GeChartOverlay extends ChartOverlay
{
	private final PriceWindow window;

	@Inject
	GeChartOverlay(ChartRenderer renderer, PriceWindow window)
	{
		super(renderer);
		this.window = window;
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.GE_OFFERS);
	}

	@Override
	Rectangle chartBounds()
	{
		return window.getChartBounds();
	}
}
