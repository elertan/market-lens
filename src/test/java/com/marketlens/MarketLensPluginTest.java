package com.marketlens;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class MarketLensPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(MarketLensPlugin.class);
		RuneLite.main(args);
	}
}
