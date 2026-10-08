package com.marketlens.ui;

import com.marketlens.price.Timeframe;
import java.awt.Rectangle;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;

/**
 * The expanded chart: the same GE-style frame, title bar and timeframe tabs as the normal window, but sized to
 * cover most of the game canvas and showing only the chart, drawn by {@link ExpandedChartOverlay}.
 * Closed with its close button, or with Esc via {@link PriceWindow}. Client thread only.
 */
@Singleton
public class ExpandedChartWindow
{
	/** Distance from the canvas edges, so a bit of the game stays visible around the window. */
	private static final int MARGIN = 24;
	private static final int CONTENT_TOP = 40;
	private static final int PAD = 10;
	private static final int TABS_TO_CHART = 4;

	private final Client client;
	private final WidgetFactory widgets;
	private final GeWidgets geWidgets;
	private final ChartState state;

	private boolean open;
	private Consumer<Timeframe> onSelectTimeframe = tf -> {};
	private Widget parent;
	private Widget root;
	private Widget chartArea;
	private TitleBar titleBar;
	private TimeframeTabs tabs;
	private int builtWidth;
	private int builtHeight;

	@Inject
	ExpandedChartWindow(Client client, WidgetFactory widgets, GeWidgets geWidgets, ChartState state)
	{
		this.client = client;
		this.widgets = widgets;
		this.geWidgets = geWidgets;
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
		open = true;
		build();
	}

	public void close()
	{
		open = false;
		removeWidgets();
	}

	/**
	 * Rebuilds the window if the game discarded our widgets or the canvas was resized.
	 *
	 * @return true if it was rebuilt, so the caller should refresh its content
	 */
	public boolean ensureAttached()
	{
		if (!open || isAttached())
		{
			return false;
		}
		removeWidgets();
		build();
		return true;
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

	private void build()
	{
		parent = canvasRoot();
		if (parent == null)
		{
			open = false;
			return;
		}

		builtWidth = parent.getWidth();
		builtHeight = parent.getHeight();
		int w = builtWidth - 2 * MARGIN;
		int h = builtHeight - 2 * MARGIN;

		root = widgets.layer(parent, MARGIN, MARGIN, w, h);
		// Swallow clicks and scrolls so nothing reaches the game underneath.
		root.setNoClickThrough(true);
		root.setNoScrollThrough(true);
		titleBar = new TitleBar(widgets, root, geWidgets.frame(root, w, h));
		geWidgets.closeButton(root, w, this::close);

		tabs = new TimeframeTabs(widgets, root, PAD, CONTENT_TOP, state.getTimeframe(), onSelectTimeframe);
		int chartTop = CONTENT_TOP + TimeframeTabs.height(widgets) + TABS_TO_CHART;
		chartArea = widgets.rect(root, Palette.CHART_BACKGROUND, 120, true, PAD, chartTop, w - 2 * PAD, h - chartTop - PAD);
		widgets.rect(root, Palette.DIVIDER, 0, false, PAD, chartTop, w - 2 * PAD, h - chartTop - PAD);
	}

	private void removeWidgets()
	{
		if (root != null)
		{
			root.deleteAllChildren();
			root.setHidden(true);
		}
		root = null;
	}

	private boolean isAttached()
	{
		return root != null
			&& parent == canvasRoot()
			&& parent.getChild(root.getIndex()) == root
			&& parent.getWidth() == builtWidth
			&& parent.getHeight() == builtHeight;
	}

	/** The top-level interface's largest root widget, which spans the game canvas. */
	private Widget canvasRoot()
	{
		Widget best = null;
		Widget[] roots = client.getWidgetRoots();
		if (roots != null)
		{
			for (Widget candidate : roots)
			{
				if (candidate != null && !candidate.isHidden()
					&& (best == null || area(candidate) > area(best)))
				{
					best = candidate;
				}
			}
		}
		return best;
	}

	private static long area(Widget widget)
	{
		return (long) widget.getWidth() * widget.getHeight();
	}
}
