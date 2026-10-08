package com.marketlens.ui;

import com.marketlens.chart.PriceFormat;
import com.marketlens.price.GeTax;
import com.marketlens.price.HourlyVolume;
import com.marketlens.price.ItemMapping;
import com.marketlens.price.LatestPrice;
import com.marketlens.price.PriceService;
import com.marketlens.price.Timeframe;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;

/**
 * The Market Lens window, built from native game widgets on top of the GE interface:
 * GE frame, title bar, back arrow, price summary, timeframe tabs and an expand button.
 * The chart itself is drawn by {@link GeChartOverlay} inside {@link #getChartBounds()}; the expand button
 * opens it in the {@link ExpandedChartWindow}. Client thread only, except {@link #keyPressed}.
 */
@Singleton
public class PriceWindow implements KeyListener
{
	/** Content starts below the frame's title divider. */
	private static final int CONTENT_TOP = 40;
	private static final int CHART_TOP = CONTENT_TOP + 20;
	private static final int TABS_TO_CHART = 4;
	private static final int PAD = 10;
	private static final int SUMMARY_WIDTH = 150;
	private static final int ROW_HEIGHT = 15;
	private static final int EXPAND_BUTTON_WIDTH = 24;

	private final Client client;
	private final ClientThread clientThread;
	private final PriceService prices;
	private final ChartState state;
	private final WidgetFactory widgets;
	private final GeWidgets geWidgets;
	private final SmallItemSprite smallItemSprite;
	private final ExpandedChartWindow expandedWindow;

	private Widget root;
	private Widget chartArea;
	private TitleBar titleBar;
	private TimeframeTabs tabs;
	private Widget buyValue;
	private Widget buyAge;
	private Widget sellValue;
	private Widget sellAge;
	private Widget marginValue;
	private Widget taxValue;
	private Widget profitValue;
	private Widget limitValue;
	private Widget volumeValue;

	@Inject
	PriceWindow(Client client, ClientThread clientThread, PriceService prices, ChartState state, WidgetFactory widgets,
		GeWidgets geWidgets, SmallItemSprite smallItemSprite, ExpandedChartWindow expandedWindow)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.prices = prices;
		this.state = state;
		this.widgets = widgets;
		this.geWidgets = geWidgets;
		this.smallItemSprite = smallItemSprite;
		this.expandedWindow = expandedWindow;
	}

	public void open(int itemId, Timeframe timeframe)
	{
		state.open(itemId, timeframe);
		build();
		refresh();
	}

	public void close()
	{
		expandedWindow.close();
		state.close();
		if (isAttached())
		{
			root.deleteAllChildren();
			root.setHidden(true);
		}
		root = null;
		chartArea = null;
		smallItemSprite.clear();
	}

	/** Rebuilds the window if the game discarded our widgets while it should be open. */
	public void ensureAttached()
	{
		if (state.isOpen() && !isAttached())
		{
			build();
			refresh();
		}
		if (expandedWindow.ensureAttached())
		{
			refresh();
		}
	}

	/** Canvas bounds of the chart area, or null when the window is not visible or the chart is expanded. */
	public Rectangle getChartBounds()
	{
		if (!state.isOpen() || !isAttached() || chartArea.isHidden() || expandedWindow.isOpen())
		{
			return null;
		}
		return chartArea.getBounds();
	}

	/** Updates all text from the latest cached prices. */
	public void refresh()
	{
		if (!state.isOpen() || !isAttached())
		{
			return;
		}

		int itemId = state.getItemId();
		ItemMapping mapping = prices.getMapping(itemId);
		String itemName = mapping != null ? mapping.getName() : client.getItemDefinition(itemId).getName();
		int itemSprite = smallItemSprite.spriteFor(itemId);
		titleBar.show(itemName, itemSprite);
		tabs.select(state.getTimeframe());
		expandedWindow.refresh(itemName, itemSprite);

		LatestPrice latest = prices.getLatest(itemId);
		Long high = latest == null ? null : latest.getHigh();
		Long low = latest == null ? null : latest.getLow();
		long now = System.currentTimeMillis() / 1000;

		buyValue.setText(high == null ? "-" : PriceFormat.exact(high));
		buyAge.setText(high == null ? "" : "Traded " + PriceFormat.age(now - latest.getHighTime()));
		sellValue.setText(low == null ? "-" : PriceFormat.exact(low));
		sellAge.setText(low == null ? "" : "Traded " + PriceFormat.age(now - latest.getLowTime()));

		if (high != null && low != null)
		{
			long margin = high - low;
			long tax = GeTax.of(itemId, high);
			long profit = margin - tax;
			marginValue.setText(PriceFormat.signed(margin));
			taxValue.setText(tax == 0 ? "0" : "-" + PriceFormat.exact(tax));
			profitValue.setText(PriceFormat.signed(profit));
			profitValue.setTextColor(profit > 0 ? Palette.BUY : profit < 0 ? Palette.SELL : Palette.WHITE);
		}
		else
		{
			marginValue.setText("-");
			taxValue.setText("-");
			profitValue.setText("-");
			profitValue.setTextColor(Palette.WHITE);
		}

		Integer limit = mapping == null ? null : mapping.getLimit();
		limitValue.setText(limit == null ? "-" : PriceFormat.exact(limit));
		HourlyVolume hourly = prices.getHourly(itemId);
		volumeValue.setText(hourly == null ? "-" : PriceFormat.exact(hourly.total()));
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE && state.isOpen())
		{
			// Esc first leaves the expanded chart, then closes Market Lens.
			e.consume();
			clientThread.invoke(() ->
			{
				if (expandedWindow.isOpen())
				{
					expandedWindow.close();
				}
				else
				{
					close();
				}
			});
		}
	}

	@Override
	public void keyTyped(KeyEvent e)
	{
	}

	@Override
	public void keyReleased(KeyEvent e)
	{
	}

	private boolean isAttached()
	{
		if (root == null)
		{
			return false;
		}
		Widget parent = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		return parent != null && parent.getChild(root.getIndex()) == root;
	}

	private void build()
	{
		Widget universe = client.getWidget(InterfaceID.GeOffers.UNIVERSE);
		if (universe == null)
		{
			state.close();
			return;
		}

		// Centre on the GE frame so the window sits on top of the GE, not the whole screen.
		Widget frame = client.getWidget(InterfaceID.GeOffers.FRAME);
		Rectangle host = frame != null ? frame.getBounds() : universe.getBounds();
		Point origin = universe.getCanvasLocation();
		int w = Math.min(GeWidgets.FRAME_WIDTH, host.width);
		int h = Math.min(GeWidgets.FRAME_HEIGHT, host.height);
		int x = host.x - origin.getX() + (host.width - w) / 2;
		int y = host.y - origin.getY() + (host.height - h) / 2;

		root = widgets.layer(universe, x, y, w, h);
		// Swallow clicks and scrolls so nothing reaches the GE underneath.
		root.setNoClickThrough(true);
		root.setNoScrollThrough(true);
		titleBar = new TitleBar(widgets, root, geWidgets.frame(root, w, h));
		geWidgets.backArrow(root, h, this::close);
		buildSummary(h);

		int chartX = PAD + SUMMARY_WIDTH + PAD;
		int chartY = CHART_TOP;
		int rowHeight = TimeframeTabs.height(widgets);
		int rowY = CHART_TOP - rowHeight - TABS_TO_CHART;
		tabs = new TimeframeTabs(widgets, root, chartX, rowY, state.getTimeframe(), this::selectTimeframe);
		geWidgets.iconButton(root, MarketLensSprite.EXPAND_ICON, "Market Lens", "Expand",
			w - PAD - EXPAND_BUTTON_WIDTH, rowY, EXPAND_BUTTON_WIDTH, rowHeight, this::expand);

		chartArea = widgets.rect(root, Palette.CHART_BACKGROUND, 120, true, chartX, chartY, w - chartX - PAD, h - chartY - PAD);
		widgets.rect(root, Palette.DIVIDER, 0, false, chartX, chartY, w - chartX - PAD, h - chartY - PAD);
	}

	private void buildSummary(int h)
	{
		int x = PAD + 4;
		int w = SUMMARY_WIDTH - 4;

		widgets.text(root, "Buy price", FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, CONTENT_TOP, w, 14);
		buyValue = widgets.text(root, "-", FontID.VERDANA_15, Palette.BUY, WidgetTextAlignment.LEFT, x, CONTENT_TOP + 14, w, 20);
		buyAge = widgets.text(root, "", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT, x, CONTENT_TOP + 34, w, 14);

		widgets.text(root, "Sell price", FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, CONTENT_TOP + 54, w, 14);
		sellValue = widgets.text(root, "-", FontID.VERDANA_15, Palette.SELL, WidgetTextAlignment.LEFT, x, CONTENT_TOP + 68, w, 20);
		sellAge = widgets.text(root, "", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT, x, CONTENT_TOP + 88, w, 14);

		widgets.rect(root, Palette.DIVIDER, 0, true, x, CONTENT_TOP + 108, w, 1);

		int y = CONTENT_TOP + 114;
		marginValue = row("Margin", x, y, w);
		taxValue = row("GE tax", x, y += ROW_HEIGHT, w);
		profitValue = row("Profit / item", x, y += ROW_HEIGHT, w);
		widgets.rect(root, Palette.DIVIDER, 0, true, x, y + ROW_HEIGHT + 3, w, 1);
		limitValue = row("Buy limit", x, y += ROW_HEIGHT + 7, w);
		volumeValue = row("Volume (1h)", x, y + ROW_HEIGHT, w);
	}

	private Widget row(String label, int x, int y, int w)
	{
		widgets.text(root, label, FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, y, w, ROW_HEIGHT);
		return widgets.text(root, "-", FontID.PLAIN_11, Palette.WHITE, WidgetTextAlignment.RIGHT, x, y, w, ROW_HEIGHT);
	}

	private void expand()
	{
		expandedWindow.open(this::selectTimeframe);
		refresh();
	}

	private void selectTimeframe(Timeframe timeframe)
	{
		state.setTimeframe(timeframe);
		refresh();
	}
}
