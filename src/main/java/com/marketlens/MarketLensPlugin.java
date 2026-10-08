package com.marketlens;

import com.google.inject.Provides;
import com.marketlens.ge.GeButtonInjector;
import com.marketlens.price.PriceService;
import com.marketlens.price.Timeframe;
import com.marketlens.ui.ChartCursor;
import com.marketlens.ui.ChartInput;
import com.marketlens.ui.ExpandedChartOverlay;
import com.marketlens.ui.ExpandedWindowInput;
import com.marketlens.ui.GeChartOverlay;
import com.marketlens.ui.MarketLensSprite;
import com.marketlens.ui.PriceWindow;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Market Lens",
	description = "Real-time Grand Exchange price charts, right inside the GE offer screen",
	tags = {"grand exchange", "ge", "price", "chart", "flipping", "wiki"}
)
public class MarketLensPlugin extends Plugin
{
	@Inject
	private ClientThread clientThread;
	@Inject
	private MarketLensConfig config;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private MouseManager mouseManager;
	@Inject
	private KeyManager keyManager;
	@Inject
	private PriceService priceService;
	@Inject
	private GeButtonInjector buttonInjector;
	@Inject
	private PriceWindow window;
	@Inject
	private GeChartOverlay chartOverlay;
	@Inject
	private ExpandedChartOverlay expandedChartOverlay;
	@Inject
	private ChartInput chartInput;
	@Inject
	private ExpandedWindowInput expandedWindowInput;
	@Inject
	private ChartCursor chartCursor;
	@Inject
	private SpriteManager spriteManager;

	@Override
	protected void startUp()
	{
		spriteManager.addSpriteOverrides(MarketLensSprite.values());
		buttonInjector.setOnClick(itemId -> window.open(itemId, openingTimeframe(), config.chartType()));
		// Start loading as soon as the user points at the button, so the data is usually ready by the click.
		buttonInjector.setOnHover(itemId -> priceService.refresh(itemId, openingTimeframe()));
		priceService.setOnUpdate(() -> clientThread.invokeLater(window::refresh));
		overlayManager.add(chartOverlay);
		overlayManager.add(expandedChartOverlay);
		// Chart first, so drags and clicks on the chart reach it before the window takes the rest.
		mouseManager.registerMouseListener(chartInput);
		mouseManager.registerMouseListener(expandedWindowInput);
		mouseManager.registerMouseWheelListener(chartInput);
		keyManager.registerKeyListener(window);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(window);
		mouseManager.unregisterMouseWheelListener(chartInput);
		mouseManager.unregisterMouseListener(expandedWindowInput);
		mouseManager.unregisterMouseListener(chartInput);
		chartCursor.update(false);
		overlayManager.remove(expandedChartOverlay);
		overlayManager.remove(chartOverlay);
		priceService.setOnUpdate(() -> {});
		priceService.clear();
		clientThread.invoke(() ->
		{
			buttonInjector.reset();
			window.close();
		});
		spriteManager.removeSpriteOverrides(MarketLensSprite.values());
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		buttonInjector.update();
		window.ensureAttached();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		window.onGameTick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MarketLensConfig.GROUP.equals(event.getGroup()))
		{
			// The chart reads the config every frame; the window's price texts need a refresh for new colours.
			clientThread.invoke(window::refresh);
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			window.close();
			buttonInjector.reset();
		}
	}

	private Timeframe openingTimeframe()
	{
		return config.rememberTimeframe() ? config.lastTimeframe() : config.defaultTimeframe();
	}

	@Provides
	MarketLensConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MarketLensConfig.class);
	}
}
