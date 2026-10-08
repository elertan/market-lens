package com.marketlens;

import com.marketlens.price.Timeframe;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MarketLensConfig.GROUP)
public interface MarketLensConfig extends Config
{
	String GROUP = "marketlens";

	@ConfigItem(
		keyName = "defaultTimeframe",
		name = "Default timeframe",
		description = "Timeframe the price chart opens with"
	)
	default Timeframe defaultTimeframe()
	{
		return Timeframe.ONE_DAY;
	}
}
