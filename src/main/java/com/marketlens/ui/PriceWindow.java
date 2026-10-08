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
import net.runelite.api.FontTypeFace;
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
 * GE frame, title, back arrow, price summary and timeframe buttons.
 * The chart itself is drawn by {@link ChartOverlay} inside {@link #getChartBounds()}.
 * Client thread only, except {@link #keyPressed}.
 */
@Singleton
public class PriceWindow implements KeyListener
{
	/** Content starts below the frame's title divider. */
	private static final int CONTENT_TOP = 40;
	private static final int CHART_TOP = CONTENT_TOP + 20;
	/** Vertical centre of the title text in the frame's title bar. */
	private static final int TITLE_CENTER_Y = 18;
	private static final int ICON_GAP = 4;
	/** Left inset of the icon and muted "Market Lens" text in the title bar, where the GE shows its History button. */
	private static final int BRAND_LEFT = 12;
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
	private final GeWidgets geWidgets;
	private final SmallItemSprite smallItemSprite;

	private Widget root;
	private Widget chartArea;
	private Widget title;
	private Widget itemIcon;
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
	PriceWindow(Client client, ClientThread clientThread, PriceService prices, ChartState state, WidgetFactory widgets,
		GeWidgets geWidgets, SmallItemSprite smallItemSprite)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.prices = prices;
		this.state = state;
		this.widgets = widgets;
		this.geWidgets = geWidgets;
		this.smallItemSprite = smallItemSprite;
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
		placeItemIcon(itemId);

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

	/** Puts the item icon just left of the centred title text. */
	private void placeItemIcon(int itemId)
	{
		FontTypeFace font = title.getFont();
		int textWidth = font != null ? font.getTextWidth(title.getText()) : title.getText().length() * 8;
		int titleCenterX = title.getOriginalX() + title.getOriginalWidth() / 2;
		smallItemSprite.show(itemId);
		itemIcon.setOriginalX(titleCenterX - textWidth / 2 - ICON_GAP - SmallItemSprite.WIDTH);
		itemIcon.revalidate();
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
		int w = Math.min(GeWidgets.FRAME_WIDTH, host.width);
		int h = Math.min(GeWidgets.FRAME_HEIGHT, host.height);
		int x = host.x - origin.getX() + (host.width - w) / 2;
		int y = host.y - origin.getY() + (host.height - h) / 2;

		root = widgets.layer(universe, x, y, w, h);
		// Swallow clicks and scrolls so nothing reaches the GE underneath.
		root.setNoClickThrough(true);
		root.setNoScrollThrough(true);
		title = geWidgets.frame(root, w, h);
		itemIcon = widgets.sprite(root, SmallItemSprite.SPRITE_ID, 0, TITLE_CENTER_Y - SmallItemSprite.HEIGHT / 2,
			SmallItemSprite.WIDTH, SmallItemSprite.HEIGHT);
		MarketLensSprite brandIcon = MarketLensSprite.CHART_ICON_SMALL;
		widgets.sprite(root, brandIcon.getSpriteId(), BRAND_LEFT, TITLE_CENTER_Y - brandIcon.getHeight() / 2,
			brandIcon.getWidth(), brandIcon.getHeight());
		widgets.text(root, "Market Lens", FontID.PLAIN_11, Palette.MUTED, WidgetTextAlignment.LEFT,
			BRAND_LEFT + brandIcon.getWidth() + ICON_GAP, 6, 120, 24);
		geWidgets.backArrow(root, h, this::close);
		buildSummary(h);
		buildTabs(w);

		int chartX = PAD + SUMMARY_WIDTH + PAD;
		int chartY = CHART_TOP;
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

	private void buildTabs(int w)
	{
		tabLabels.clear();
		Dimension leftSize = widgets.spriteSize(SpriteID.GeTextbackdrop.LEFT, new Dimension(5, 18));
		Dimension rightSize = widgets.spriteSize(SpriteID.GeTextbackdrop.RIGHT, new Dimension(5, 18));
		int tabHeight = leftSize.height;
		int y = CHART_TOP - tabHeight - 4;
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
