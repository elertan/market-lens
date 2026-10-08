package com.marketlens;

import com.google.inject.Provides;
import com.marketlens.ge.GeIconInjector;
import com.marketlens.price.PriceService;
import com.marketlens.ui.ChartInput;
import com.marketlens.ui.ChartOverlay;
import com.marketlens.ui.PriceWindow;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
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
	private Client client;
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
	private GeIconInjector iconInjector;
	@Inject
	private PriceWindow window;
	@Inject
	private ChartOverlay chartOverlay;
	@Inject
	private ChartInput chartInput;

	@Override
	protected void startUp()
	{
		iconInjector.setOnClick(itemId -> window.open(itemId, config.defaultTimeframe()));
		priceService.setOnUpdate(() -> clientThread.invokeLater(window::refresh));
		overlayManager.add(chartOverlay);
		mouseManager.registerMouseListener(chartInput);
		mouseManager.registerMouseWheelListener(chartInput);
		keyManager.registerKeyListener(window);
	}

	@Override
	protected void shutDown()
	{
		keyManager.unregisterKeyListener(window);
		mouseManager.unregisterMouseWheelListener(chartInput);
		mouseManager.unregisterMouseListener(chartInput);
		overlayManager.remove(chartOverlay);
		priceService.setOnUpdate(() -> {});
		priceService.clear();
		clientThread.invoke(() ->
		{
			iconInjector.reset();
			window.close();
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		iconInjector.update();
		window.ensureAttached();
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (client.getWidget(InterfaceID.GeOffers.UNIVERSE) != null)
		{
			priceService.refreshIfStale();
			// Keeps the "Traded 2m ago" texts current.
			window.refresh();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.GE_OFFERS)
		{
			window.close();
			iconInjector.reset();
		}
	}

	@Provides
	MarketLensConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MarketLensConfig.class);
	}
}
