package com.marketlens.ui;

import com.marketlens.price.Timeframe;
import java.awt.Rectangle;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

/**
 * The expanded chart: the same GE-style frame, title bar and timeframe tabs as the normal window, but sized to
 * cover most of the game canvas and showing only the chart, drawn by {@link ExpandedChartOverlay}.
 * Closed with its close button, or with Esc via {@link PriceWindow}. Clicks inside it are taken by
 * {@link ExpandedWindowInput}, which calls {@link #clickAt}. Client thread only, except {@link #getBounds}.
 */
@Singleton
public class ExpandedChartWindow extends GeWindow
{
	/** Distance from the canvas edges, so a bit of the game stays visible around the window. */
	private static final int MARGIN = 24;
	private static final int CONTENT_TOP = 40;
	private static final int PAD = 10;
	private static final int TABS_TO_CHART = 4;

	private final Client client;
	private final ChartState state;

	private boolean open;
	private Consumer<Timeframe> onSelectTimeframe = tf -> {};
	private Widget chartArea;
	private TitleBar titleBar;
	private TimeframeTabs tabs;
	private Widget closeButton;
	/**
	 * Canvas bounds of the whole window, read by mouse input on the AWT thread. Updated every client tick:
	 * new widgets only get a screen position once the client has drawn them.
	 */
	private volatile Rectangle bounds;

	@Inject
	ExpandedChartWindow(Client client, WidgetFactory widgets, GeWidgets geWidgets, ChartState state)
	{
		super(widgets, geWidgets);
		this.client = client;
		this.state = state;
	}

	public boolean isOpen()
	{
		return open;
	}

	/** @param onSelectTimeframe called when a timeframe tab is clicked */
	public void open(Consumer<Timeframe> onSelectTimeframe)
	{
		this.onSelectTimeframe = onSelectTimeframe;
		open = attach();
	}

	public void close()
	{
		open = false;
		detach();
		bounds = null;
	}

	/**
	 * Every client tick: rebuilds the window if the game discarded it or the canvas was resized,
	 * and records where it is on screen.
	 *
	 * @return true if it was rebuilt, so the caller should refresh its content
	 */
	public boolean ensureAttached()
	{
		if (!open)
		{
			return false;
		}
		boolean rebuilt = reattachIfLost();
		bounds = rootBounds();
		return rebuilt;
	}

	/** Canvas bounds of the whole window, or null when it is closed. Safe to call from any thread. */
	public Rectangle getBounds()
	{
		return bounds;
	}

	/** Canvas bounds of the chart area, or null when the window is not showing. */
	public Rectangle getChartBounds()
	{
		return open && isAttached() ? chartArea.getBounds() : null;
	}

	public void refresh(String itemName, int itemSpriteId)
	{
		if (open && isAttached())
		{
			titleBar.show(itemName, itemSpriteId);
			tabs.select(state.getTimeframe());
		}
	}

	/** Performs a left click at canvas point x,y: the close button or a timeframe tab. */
	public void clickAt(int x, int y)
	{
		if (!open || !isAttached())
		{
			return;
		}
		if (closeButton.getBounds().contains(x, y))
		{
			close();
			return;
		}
		Timeframe timeframe = tabs.at(x, y);
		if (timeframe != null)
		{
			onSelectTimeframe.accept(timeframe);
		}
	}

	/**
	 * The top-level interface's UI highlights layer. It spans the whole canvas and is drawn after everything
	 * else, so the window covers the game view, chat box and side panels, and blocks mouse input to them.
	 * Every client layout (fixed, resizable classic/modern, ...) has one.
	 */
	@Override
	protected Widget findHost()
	{
		switch (client.getTopLevelInterfaceId())
		{
			case InterfaceID.TOPLEVEL:
				return client.getWidget(InterfaceID.Toplevel.UI_HIGHLIGHTS);
			case InterfaceID.TOPLEVEL_OSRS_STRETCH:
				return client.getWidget(InterfaceID.ToplevelOsrsStretch.UI_HIGHLIGHTS);
			case InterfaceID.TOPLEVEL_PRE_EOC:
				return client.getWidget(InterfaceID.ToplevelPreEoc.UI_HIGHLIGHTS);
			case InterfaceID.TOPLEVEL_OSM:
				return client.getWidget(InterfaceID.ToplevelOsm.UI_HIGHLIGHTS);
			case InterfaceID.TOPLEVEL_DISPLAY:
				return client.getWidget(InterfaceID.ToplevelDisplay.UI_HIGHLIGHTS);
			case InterfaceID.TOPLEVEL_SPECTATOR:
				return client.getWidget(InterfaceID.ToplevelSpectator.UI_HIGHLIGHTS);
			default:
				return null;
		}
	}

	@Override
	protected Rectangle placement(Widget host)
	{
		return new Rectangle(MARGIN, MARGIN, host.getWidth() - 2 * MARGIN, host.getHeight() - 2 * MARGIN);
	}

	@Override
	protected void buildContent(Widget root, int w, int h)
	{
		titleBar = new TitleBar(widgets, root, geWidgets.frame(root, w, h));
		closeButton = geWidgets.closeButton(root, w, this::close);

		tabs = new TimeframeTabs(widgets, root, PAD, CONTENT_TOP, state.getTimeframe(), onSelectTimeframe);
		int chartTop = CONTENT_TOP + TimeframeTabs.height(widgets) + TABS_TO_CHART;
		chartArea = geWidgets.chartPanel(root, PAD, chartTop, w - 2 * PAD, h - chartTop - PAD);
	}
}
