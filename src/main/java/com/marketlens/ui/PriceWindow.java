package com.marketlens.ui;

import com.marketlens.MarketLensConfig;
import com.marketlens.chart.ChartType;
import com.marketlens.chart.PriceFormat;
import com.marketlens.price.GeTax;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyListener;

/**
 * The Market Lens window, built from native game widgets on top of the GE interface:
 * GE frame, title bar, back arrow, price summary, timeframe tabs, a line/candles toggle and an expand button.
 * The chart itself is drawn by {@link GeChartOverlay} inside {@link #getChartBounds()}; the expand button
 * opens it in the {@link ExpandedChartWindow}. Client thread only, except {@link #keyPressed}.
 */
@Singleton
public class PriceWindow extends GeWindow implements KeyListener
{
	/** Content starts below the frame's title divider. */
	private static final int CONTENT_TOP = 40;
	/** Row above the chart with the timeframe tabs and the expand button, which is taller than the tabs. */
	private static final int TAB_ROW_HEIGHT = 30;
	private static final int TABS_TO_CHART = 4;
	private static final int CHART_TOP = CONTENT_TOP + TAB_ROW_HEIGHT;
	private static final int PAD = 10;
	private static final int SUMMARY_WIDTH = 150;
	private static final int ROW_HEIGHT = 15;
	private static final int TOGGLE_TO_EXPAND = 4;
	private static final int EXPAND_BUTTON_WIDTH = 26;
	private static final int EXPAND_BUTTON_HEIGHT = 30;

	private final Client client;
	private final ClientThread clientThread;
	private final PriceService prices;
	private final ChartState state;
	private final SmallItemSprite smallItemSprite;
	private final ExpandedChartWindow expandedWindow;
	private final ConfigManager configManager;

	private Widget chartArea;
	private TitleBar titleBar;
	private TimeframeTabs tabs;
	private ChartTypeToggle chartTypeToggle;
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
		GeWidgets geWidgets, SmallItemSprite smallItemSprite, ExpandedChartWindow expandedWindow,
		ConfigManager configManager)
	{
		super(widgets, geWidgets);
		this.client = client;
		this.clientThread = clientThread;
		this.prices = prices;
		this.state = state;
		this.smallItemSprite = smallItemSprite;
		this.expandedWindow = expandedWindow;
		this.configManager = configManager;
	}

	public void open(int itemId, Timeframe timeframe, ChartType chartType)
	{
		state.open(itemId, timeframe);
		state.setChartType(chartType);
		prices.refresh(itemId, timeframe);
		if (attach())
		{
			refresh();
		}
		else
		{
			state.close();
		}
	}

	public void close()
	{
		expandedWindow.close();
		state.close();
		detach();
		smallItemSprite.clear();
	}

	/** Every client tick: rebuilds the windows if the game discarded their widgets while they should be open. */
	public void ensureAttached()
	{
		boolean rebuilt = state.isOpen() && reattachIfLost();
		if (expandedWindow.ensureAttached() || rebuilt)
		{
			refresh();
		}
	}

	/** Every game tick while open: keeps the prices current and the "Traded ... ago" texts ticking. */
	public void onGameTick()
	{
		if (state.isOpen())
		{
			prices.refresh(state.getItemId(), state.getTimeframe());
			refreshTradeAges();
		}
	}

	/** Canvas bounds of the chart area, or null when the window is not visible or the chart is expanded. */
	public Rectangle getChartBounds()
	{
		if (!state.isOpen() || !isAttached() || expandedWindow.isOpen())
		{
			return null;
		}
		return chartArea.getBounds();
	}

	/** Updates everything shown from the cached price data. Called when the window opens and when data arrives. */
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
		chartTypeToggle.select(state.getChartType());
		expandedWindow.refresh(itemName, itemSprite);

		LatestPrice latest = prices.getLatest(itemId);
		Long high = latest == null ? null : latest.getHigh();
		Long low = latest == null ? null : latest.getLow();
		buyValue.setText(high == null ? "-" : PriceFormat.exact(high));
		sellValue.setText(low == null ? "-" : PriceFormat.exact(low));
		refreshTradeAges();

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
		Long hourVolume = prices.getHourVolume(itemId);
		volumeValue.setText(hourVolume == null ? "-" : PriceFormat.exact(hourVolume));
	}

	/** Only the "Traded ... ago" texts, which change with time rather than with data. */
	private void refreshTradeAges()
	{
		if (!state.isOpen() || !isAttached())
		{
			return;
		}
		LatestPrice latest = prices.getLatest(state.getItemId());
		long now = System.currentTimeMillis() / 1000;
		buyAge.setText(latest == null || latest.getHigh() == null ? "" : "Traded " + PriceFormat.age(now - latest.getHighTime()));
		sellAge.setText(latest == null || latest.getLow() == null ? "" : "Traded " + PriceFormat.age(now - latest.getLowTime()));
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

	@Override
	protected Widget findHost()
	{
		return client.getWidget(InterfaceID.GeOffers.UNIVERSE);
	}

	/** Exactly over the GE's own frame, so the window replaces the GE view rather than floating on the screen. */
	@Override
	protected Rectangle placement(Widget universe)
	{
		Widget frame = client.getWidget(InterfaceID.GeOffers.FRAME);
		Rectangle target = frame != null ? frame.getBounds() : universe.getBounds();
		Point origin = universe.getCanvasLocation();
		int w = Math.min(GeWidgets.FRAME_WIDTH, target.width);
		int h = Math.min(GeWidgets.FRAME_HEIGHT, target.height);
		return new Rectangle(target.x - origin.getX() + (target.width - w) / 2,
			target.y - origin.getY() + (target.height - h) / 2, w, h);
	}

	@Override
	protected void buildContent(Widget root, int w, int h)
	{
		titleBar = new TitleBar(widgets, root, geWidgets.frame(root, w, h));
		geWidgets.backArrow(root, h, this::close);
		buildSummary(root);

		int chartX = PAD + SUMMARY_WIDTH + PAD;
		int rowY = CHART_TOP - TAB_ROW_HEIGHT - TABS_TO_CHART;
		int tabsY = rowY + (TAB_ROW_HEIGHT - TimeframeTabs.height(widgets)) / 2;
		tabs = new TimeframeTabs(widgets, root, chartX, tabsY, state.getTimeframe(), this::selectTimeframe);
		int expandX = w - PAD - EXPAND_BUTTON_WIDTH;
		chartTypeToggle = new ChartTypeToggle(geWidgets, root, expandX - TOGGLE_TO_EXPAND - ChartTypeToggle.WIDTH,
			rowY, state.getChartType(), this::selectChartType);
		geWidgets.iconButton(root, MarketLensSprite.EXPAND_ICON, "Market Lens", "Expand",
			expandX, rowY + (TAB_ROW_HEIGHT - EXPAND_BUTTON_HEIGHT) / 2,
			EXPAND_BUTTON_WIDTH, EXPAND_BUTTON_HEIGHT, this::expand, () -> {});

		chartArea = geWidgets.chartPanel(root, chartX, CHART_TOP, w - chartX - PAD, h - CHART_TOP - PAD);
	}

	private void buildSummary(Widget root)
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
		marginValue = row(root, "Margin", x, y, w);
		taxValue = row(root, "GE tax", x, y += ROW_HEIGHT, w);
		profitValue = row(root, "Profit / item", x, y += ROW_HEIGHT, w);
		widgets.rect(root, Palette.DIVIDER, 0, true, x, y + ROW_HEIGHT + 3, w, 1);
		limitValue = row(root, "Buy limit", x, y += ROW_HEIGHT + 7, w);
		volumeValue = row(root, "Volume (1h)", x, y + ROW_HEIGHT, w);
	}

	private Widget row(Widget root, String label, int x, int y, int w)
	{
		widgets.text(root, label, FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, y, w, ROW_HEIGHT);
		return widgets.text(root, "-", FontID.PLAIN_11, Palette.WHITE, WidgetTextAlignment.RIGHT, x, y, w, ROW_HEIGHT);
	}

	private void expand()
	{
		expandedWindow.open(this::selectTimeframe, this::selectChartType);
		refresh();
	}

	/** Switches line/candles in both windows and remembers the choice in the plugin config. */
	private void selectChartType(ChartType type)
	{
		state.setChartType(type);
		configManager.setConfiguration(MarketLensConfig.GROUP, MarketLensConfig.CHART_TYPE_KEY, type);
		refresh();
	}

	private void selectTimeframe(Timeframe timeframe)
	{
		state.setTimeframe(timeframe);
		prices.refresh(state.getItemId(), timeframe);
		refresh();
	}
}
