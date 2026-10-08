package com.marketlens.ui;

import java.awt.Rectangle;
import net.runelite.api.widgets.Widget;

/**
 * Lifecycle shared by the Market Lens windows: a layer of native widgets on a host widget of the game.
 * The game can discard dynamic widgets at any time (and the host can be resized), so the window checks
 * every client tick whether it is still in place. Subclasses say where it goes and what it contains.
 * Client thread only.
 */
abstract class GeWindow
{
	protected final WidgetFactory widgets;
	protected final GeWidgets geWidgets;

	private Widget host;
	private Widget root;
	private int hostWidth;
	private int hostHeight;

	GeWindow(WidgetFactory widgets, GeWidgets geWidgets)
	{
		this.widgets = widgets;
		this.geWidgets = geWidgets;
	}

	/** The widget to build the window on, or null if it isn't available right now. */
	protected abstract Widget findHost();

	/** Position and size of the window, relative to {@code host}. */
	protected abstract Rectangle placement(Widget host);

	/** Builds the window's content into {@code root}, which is {@code w} x {@code h}. */
	protected abstract void buildContent(Widget root, int w, int h);

	/** Builds the window. Returns false if its host isn't available. */
	protected final boolean attach()
	{
		host = findHost();
		if (host == null)
		{
			return false;
		}
		hostWidth = host.getWidth();
		hostHeight = host.getHeight();

		Rectangle at = placement(host);
		root = widgets.layer(host, at.x, at.y, at.width, at.height);
		// Swallow clicks and scrolls so nothing reaches whatever is underneath.
		root.setNoClickThrough(true);
		root.setNoScrollThrough(true);
		buildContent(root, at.width, at.height);
		return true;
	}

	/** Removes the window's widgets, if any. */
	protected final void detach()
	{
		if (root != null)
		{
			root.deleteAllChildren();
			root.setHidden(true);
		}
		root = null;
	}

	/** Whether the window's widgets are still in place on an unchanged host. */
	protected final boolean isAttached()
	{
		return root != null
			&& host == findHost()
			&& host.getChild(root.getIndex()) == root
			&& host.getWidth() == hostWidth
			&& host.getHeight() == hostHeight;
	}

	/** Rebuilds the window if the game discarded it or its host was resized. Returns true if it was rebuilt. */
	protected final boolean reattachIfLost()
	{
		if (isAttached())
		{
			return false;
		}
		detach();
		return attach();
	}

	/** Canvas bounds of the whole window, or null when it isn't built. */
	protected final Rectangle rootBounds()
	{
		return root == null ? null : root.getBounds();
	}
}
