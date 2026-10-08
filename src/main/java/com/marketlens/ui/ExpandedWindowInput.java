package com.marketlens.ui;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.MouseListener;

/**
 * Takes every mouse click inside the expanded window before the game sees it, and performs the window's
 * own button actions instead. Needed because parts of the game, like the minimap, react to raw clicks
 * rather than menu options, so blocking click-through on the widgets alone isn't enough.
 * Mouse movement is left alone, so hover text and tooltips keep working. Runs on the AWT thread.
 */
@Singleton
public class ExpandedWindowInput implements MouseListener
{
	private final ExpandedChartWindow window;
	private final ClientThread clientThread;

	@Inject
	ExpandedWindowInput(ExpandedChartWindow window, ClientThread clientThread)
	{
		this.window = window;
		this.clientThread = clientThread;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		if (insideWindow(e) && e.getButton() == MouseEvent.BUTTON1)
		{
			int x = e.getX();
			int y = e.getY();
			clientThread.invoke(() -> window.clickAt(x, y));
		}
		return consumeIfInside(e);
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		return consumeIfInside(e);
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		return consumeIfInside(e);
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		return e;
	}

	private boolean insideWindow(MouseEvent e)
	{
		Rectangle bounds = window.getBounds();
		return bounds != null && bounds.contains(e.getPoint());
	}

	private MouseEvent consumeIfInside(MouseEvent e)
	{
		if (insideWindow(e))
		{
			e.consume();
		}
		return e;
	}
}
