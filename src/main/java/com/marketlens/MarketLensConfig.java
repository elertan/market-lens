package com.marketlens;

import com.marketlens.chart.ChartType;
import com.marketlens.price.Timeframe;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MarketLensConfig.GROUP)
public interface MarketLensConfig extends Config
{
	String GROUP = "marketlens";
	String CHART_TYPE_KEY = "chartType";

	@ConfigItem(
		keyName = "defaultTimeframe",
		name = "Default timeframe",
		description = "Timeframe the price chart opens with"
	)
	default Timeframe defaultTimeframe()
	{
		return Timeframe.ONE_DAY;
	}

	@ConfigItem(
		keyName = CHART_TYPE_KEY,
		name = "Chart type",
		description = "Line or candlestick chart. Also changed, and remembered, with the buttons above the chart."
	)
	default ChartType chartType()
	{
		return ChartType.LINE;
	}
}
