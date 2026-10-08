package com.marketlens.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.ClientUI;

/**
 * TradingView-style cross cursor over the chart. Leaving the chart restores the client's default cursor, which
 * is the one the Custom Cursor plugin set, if any: {@link ClientUI#setCursor(Cursor)} does not replace it.
 * AWT thread.
 */
@Singleton
public class ChartCursor
{
	/** Arm length from the centre pixel. */
	private static final int ARM = 7;

	private final ClientUI clientUI;
	private Cursor cross;
	private boolean shown;

	@Inject
	ChartCursor(ClientUI clientUI)
	{
		this.clientUI = clientUI;
	}

	/** Shows the cross while {@code overChart}, the default cursor otherwise. Cheap to call on every mouse move. */
	public void update(boolean overChart)
	{
		if (overChart == shown)
		{
			return;
		}
		shown = overChart;
		clientUI.setCursor(overChart ? cross() : clientUI.getDefaultCursor());
	}

	private Cursor cross()
	{
		if (cross == null)
		{
			cross = createCross();
		}
		return cross;
	}

	/** A 1-px white cross with a dark outline, so it stays visible on the light chart text and grid too. */
	private static Cursor createCross()
	{
		Toolkit toolkit = Toolkit.getDefaultToolkit();
		Dimension size = toolkit.getBestCursorSize(2 * ARM + 3, 2 * ARM + 3);
		BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
		int c = Math.min(size.width, size.height) / 2;
		Graphics2D g = image.createGraphics();
		g.setColor(Color.BLACK);
		g.fillRect(c - ARM - 1, c - 1, 2 * ARM + 3, 3);
		g.fillRect(c - 1, c - ARM - 1, 3, 2 * ARM + 3);
		g.setColor(Color.WHITE);
		g.fillRect(c - ARM, c, 2 * ARM + 1, 1);
		g.fillRect(c, c - ARM, 1, 2 * ARM + 1);
		g.dispose();
		return toolkit.createCustomCursor(image, new Point(c, c), "Market Lens chart");
	}
}
