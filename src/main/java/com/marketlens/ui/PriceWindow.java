package com.marketlens.ui;

import com.marketlens.chart.PriceFormat;
import com.marketlens.price.GeTax;
import com.marketlens.price.HourlyVolume;
import com.marketlens.price.ItemMapping;
import com.marketlens.price.LatestPrice;
import com.marketlens.price.PriceService;
import com.marketlens.price.Timeframe;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.SpriteID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyListener;

/**
 * The Market Lens window, built from native game widgets on top of the GE interface:
 * steel border frame, title, close button, price summary and timeframe buttons.
 * The chart itself is drawn by {@link ChartOverlay} inside {@link #getChartBounds()}.
 * Client thread only, except {@link #keyPressed}.
 */
@Singleton
public class PriceWindow implements KeyListener
{
	private static final int MAX_WIDTH = 488;
	private static final int MAX_HEIGHT = 300;
	private static final int PAD = 10;
	private static final int SUMMARY_WIDTH = 150;
	private static final int ROW_HEIGHT = 15;
	private static final int TAB_WIDTH = 34;
	private static final int TAB_GAP = 2;

	private final Client client;
	private final ClientThread clientThread;
	private final PriceService prices;
	private final ChartState state;
	private final WidgetFactory widgets;

	private Widget root;
	private Widget chartArea;
	private Widget title;
	private Widget buyValue;
	private Widget buyAge;
	private Widget sellValue;
	private Widget sellAge;
	private Widget marginValue;
	private Widget taxValue;
	private Widget profitValue;
	private Widget limitValue;
	private Widget volumeValue;
	private final Map<Timeframe, Widget> tabLabels = new EnumMap<>(Timeframe.class);

	@Inject
	PriceWindow(Client client, ClientThread clientThread, PriceService prices, ChartState state, WidgetFactory widgets)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.prices = prices;
		this.state = state;
		this.widgets = widgets;
	}

	public void open(int itemId, Timeframe timeframe)
	{
		state.open(itemId, timeframe);
		build();
		refresh();
	}

	public void close()
	{
		state.close();
		if (isAttached())
		{
			root.deleteAllChildren();
			root.setHidden(true);
		}
		root = null;
		chartArea = null;
	}

	/** Rebuilds the window if the game discarded our widgets while it should be open. */
	public void ensureAttached()
	{
		if (state.isOpen() && !isAttached())
		{
			build();
			refresh();
		}
	}

	/** Canvas bounds of the chart area, or null when the window is not visible. */
	public Rectangle getChartBounds()
	{
		if (!state.isOpen() || !isAttached() || chartArea.isHidden())
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
		title.setText(mapping != null ? mapping.getName() : client.getItemDefinition(itemId).getName());

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

		for (Map.Entry<Timeframe, Widget> tab : tabLabels.entrySet())
		{
			tab.getValue().setTextColor(tab.getKey() == state.getTimeframe() ? Palette.WHITE : Palette.ORANGE);
		}
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		if (e.getKeyCode() == KeyEvent.VK_ESCAPE && state.isOpen())
		{
			e.consume();
			clientThread.invoke(this::close);
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
		int w = Math.min(MAX_WIDTH, host.width);
		int h = Math.min(MAX_HEIGHT, host.height);
		int x = host.x - origin.getX() + (host.width - w) / 2;
		int y = host.y - origin.getY() + (host.height - h) / 2;

		root = widgets.layer(universe, x, y, w, h);
		buildFrame(w, h);
		buildTitleBar(w);
		buildSummary(h);
		buildTabs(w);

		int chartX = PAD + SUMMARY_WIDTH + PAD;
		int chartY = 52;
		chartArea = widgets.rect(root, Palette.CHART_BACKGROUND, 120, true, chartX, chartY, w - chartX - PAD, h - chartY - PAD);
		widgets.rect(root, Palette.DIVIDER, 0, false, chartX, chartY, w - chartX - PAD, h - chartY - PAD);
	}

	private void buildFrame(int w, int h)
	{
		// Background swallows clicks and scrolls so nothing reaches the GE underneath.
		Widget background = widgets.tiledSprite(root, SpriteID.TRADEBACKING_DARK, 0, 0, w, h);
		background.setNoClickThrough(true);
		background.setNoScrollThrough(true);

		Dimension tl = widgets.spriteSize(SpriteID.Steelborder.TOP_LEFT, new Dimension(25, 30));
		Dimension tr = widgets.spriteSize(SpriteID.Steelborder.TOP_RIGHT, new Dimension(25, 30));
		Dimension bl = widgets.spriteSize(SpriteID.Steelborder.BOTTOM_LEFT, new Dimension(25, 30));
		Dimension br = widgets.spriteSize(SpriteID.Steelborder.BOTTOM_RIGHT, new Dimension(25, 30));
		Dimension edgeH = widgets.spriteSize(SpriteID.Steelborder2.EDGE_TOP, new Dimension(32, 6));
		Dimension edgeV = widgets.spriteSize(SpriteID.Steelborder2.EDGE_RIGHT, new Dimension(6, 32));

		widgets.tiledSprite(root, SpriteID.Steelborder2.EDGE_TOP, tl.width, 0, w - tl.width - tr.width, edgeH.height);
		Widget bottom = widgets.tiledSprite(root, SpriteID.Steelborder2.EDGE_TOP, bl.width, h - edgeH.height, w - bl.width - br.width, edgeH.height);
		bottom.setFlippedVertically(true);
		widgets.tiledSprite(root, SpriteID.Steelborder2.EDGE_RIGHT, w - edgeV.width, tr.height, edgeV.width, h - tr.height - br.height);
		Widget left = widgets.tiledSprite(root, SpriteID.Steelborder2.EDGE_RIGHT, 0, tl.height, edgeV.width, h - tl.height - bl.height);
		left.setFlippedHorizontally(true);

		widgets.sprite(root, SpriteID.Steelborder.TOP_LEFT, 0, 0, tl.width, tl.height);
		widgets.sprite(root, SpriteID.Steelborder.TOP_RIGHT, w - tr.width, 0, tr.width, tr.height);
		widgets.sprite(root, SpriteID.Steelborder.BOTTOM_LEFT, 0, h - bl.height, bl.width, bl.height);
		widgets.sprite(root, SpriteID.Steelborder.BOTTOM_RIGHT, w - br.width, h - br.height, br.width, br.height);
	}

	private void buildTitleBar(int w)
	{
		title = widgets.text(root, "", FontID.BOLD_12, Palette.ORANGE, WidgetTextAlignment.CENTER, 0, 6, w, 18);

		Dimension size = widgets.spriteSize(SpriteID.CloseButtons.BUTTON, new Dimension(26, 23));
		Widget close = widgets.sprite(root, SpriteID.CloseButtons.BUTTON, w - size.width - 6, 6, size.width, size.height);
		close.setName("<col=ff9040>Market Lens</col>");
		close.setAction(0, "Close");
		close.setHasListener(true);
		close.setOnOpListener((JavaScriptCallback) e -> close());
		close.setOnMouseOverListener((JavaScriptCallback) e -> close.setSpriteId(SpriteID.CloseButtons.HOVERED));
		close.setOnMouseLeaveListener((JavaScriptCallback) e -> close.setSpriteId(SpriteID.CloseButtons.BUTTON));
	}

	private void buildSummary(int h)
	{
		int x = PAD + 4;
		int w = SUMMARY_WIDTH - 4;

		widgets.text(root, "Buy price", FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, 32, w, 14);
		buyValue = widgets.text(root, "-", FontID.VERDANA_15, Palette.BUY, WidgetTextAlignment.LEFT, x, 46, w, 20);
		buyAge = widgets.text(root, "", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT, x, 66, w, 14);

		widgets.text(root, "Sell price", FontID.PLAIN_11, Palette.ORANGE, WidgetTextAlignment.LEFT, x, 86, w, 14);
		sellValue = widgets.text(root, "-", FontID.VERDANA_15, Palette.SELL, WidgetTextAlignment.LEFT, x, 100, w, 20);
		sellAge = widgets.text(root, "", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT, x, 120, w, 14);

		widgets.rect(root, Palette.DIVIDER, 0, true, x, 140, w, 1);

		int y = 146;
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

	private void buildTabs(int w)
	{
		tabLabels.clear();
		Dimension leftSize = widgets.spriteSize(SpriteID.GeTextbackdrop.LEFT, new Dimension(5, 18));
		Dimension rightSize = widgets.spriteSize(SpriteID.GeTextbackdrop.RIGHT, new Dimension(5, 18));
		int tabHeight = leftSize.height;
		int y = 52 - tabHeight - 4;
		int x = w - PAD - Timeframe.values().length * (TAB_WIDTH + TAB_GAP) + TAB_GAP;

		for (Timeframe timeframe : Timeframe.values())
		{
			widgets.sprite(root, SpriteID.GeTextbackdrop.LEFT, x, y, leftSize.width, tabHeight);
			widgets.tiledSprite(root, SpriteID.GeTextbackdrop.MIDDLE, x + leftSize.width, y,
				TAB_WIDTH - leftSize.width - rightSize.width, tabHeight);
			widgets.sprite(root, SpriteID.GeTextbackdrop.RIGHT, x + TAB_WIDTH - rightSize.width, y, rightSize.width, tabHeight);

			Widget label = widgets.text(root, timeframe.getLabel(), FontID.PLAIN_11, Palette.ORANGE,
				WidgetTextAlignment.CENTER, x, y, TAB_WIDTH, tabHeight);
			label.setName("<col=ff9040>" + timeframe.getLabel() + "</col>");
			label.setAction(0, "View");
			label.setHasListener(true);
			label.setOnOpListener((JavaScriptCallback) e -> selectTimeframe(timeframe));
			label.setOnMouseOverListener((JavaScriptCallback) e -> label.setTextColor(Palette.YELLOW));
			label.setOnMouseLeaveListener((JavaScriptCallback) e ->
				label.setTextColor(timeframe == state.getTimeframe() ? Palette.WHITE : Palette.ORANGE));
			tabLabels.put(timeframe, label);

			x += TAB_WIDTH + TAB_GAP;
		}
	}

	private void selectTimeframe(Timeframe timeframe)
	{
		state.setTimeframe(timeframe);
		refresh();
	}
}
