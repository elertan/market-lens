package com.marketlens.ui;

import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.input.MouseListener;
import net.runelite.client.input.MouseWheelListener;

/**
 * Chart mouse handling on the AWT thread: hover for the crosshair, wheel to zoom (anchored on the right
 * edge, or on the cursor with Shift/Ctrl), left-drag to pan, double-click to reset. Events inside the plot
 * are consumed so the game camera does not zoom or walk.
 */
@Singleton
public class ChartInput implements MouseListener, MouseWheelListener
{
	private static final double ZOOM_STEP = 1.15;

	private final ChartState state;
	private int dragLastX = -1;

	@Inject
	ChartInput(ChartState state)
	{
		this.state = state;
	}

	@Override
	public MouseWheelEvent mouseWheelMoved(MouseWheelEvent e)
	{
		Rectangle plot = state.getPlotBounds();
		if (plot == null || !plot.contains(e.getPoint()))
		{
			return e;
		}
		// Like TradingView: the wheel zooms around the newest visible data (only the left edge moves);
		// with Shift or Ctrl held it zooms around the cursor. Wheel down (positive rotation) zooms out.
		double anchor = e.isShiftDown() || e.isControlDown() ? timeAt(plot, e.getX()) : state.getViewport().getEnd();
		state.getViewport().zoom(Math.pow(ZOOM_STEP, e.getPreciseWheelRotation()), anchor);
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent e)
	{
		Rectangle plot = state.getPlotBounds();
		if (e.getButton() == MouseEvent.BUTTON1 && plot != null && plot.contains(e.getPoint()))
		{
			dragLastX = e.getX();
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseDragged(MouseEvent e)
	{
		Rectangle plot = state.getPlotBounds();
		if (dragLastX < 0 || plot == null)
		{
			return e;
		}
		double span = state.getViewport().getEnd() - state.getViewport().getStart();
		state.getViewport().pan(-(e.getX() - dragLastX) * span / plot.width);
		dragLastX = e.getX();
		state.setHover(e.getX(), e.getY());
		e.consume();
		return e;
	}

	@Override
	public MouseEvent mouseReleased(MouseEvent e)
	{
		if (dragLastX >= 0)
		{
			dragLastX = -1;
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseClicked(MouseEvent e)
	{
		Rectangle plot = state.getPlotBounds();
		if (plot != null && plot.contains(e.getPoint()))
		{
			if (e.getClickCount() >= 2)
			{
				state.getViewport().reset();
			}
			e.consume();
		}
		return e;
	}

	@Override
	public MouseEvent mouseMoved(MouseEvent e)
	{
		Rectangle plot = state.getPlotBounds();
		if (plot != null && plot.contains(e.getPoint()))
		{
			state.setHover(e.getX(), e.getY());
		}
		else
		{
			state.clearHover();
		}
		return e;
	}

	@Override
	public MouseEvent mouseEntered(MouseEvent e)
	{
		return e;
	}

	@Override
	public MouseEvent mouseExited(MouseEvent e)
	{
		state.clearHover();
		return e;
	}

	private double timeAt(Rectangle plot, int x)
	{
		double start = state.getViewport().getStart();
		double end = state.getViewport().getEnd();
		return start + (double) (x - plot.x) / plot.width * (end - start);
	}
}
