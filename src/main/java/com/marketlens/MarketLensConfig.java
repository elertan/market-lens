package com.marketlens;

import com.marketlens.chart.ChartType;
import com.marketlens.chart.TimeFormat;
import com.marketlens.price.Timeframe;
import com.marketlens.ui.Palette;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(MarketLensConfig.GROUP)
public interface MarketLensConfig extends Config
{
	String GROUP = "marketlens";
	String CHART_TYPE_KEY = "chartType";
	String LAST_TIMEFRAME_KEY = "lastTimeframe";

	@ConfigItem(
		keyName = "defaultTimeframe",
		name = "Default timeframe",
		description = "Timeframe the price chart opens with",
		position = 1
	)
	default Timeframe defaultTimeframe()
	{
		return Timeframe.ONE_DAY;
	}

	@ConfigItem(
		keyName = "rememberTimeframe",
		name = "Remember last timeframe",
		description = "Open the chart with the timeframe you used last, instead of the default timeframe",
		position = 2
	)
	default boolean rememberTimeframe()
	{
		return false;
	}

	/** Last timeframe picked with the tabs; used instead of the default when {@link #rememberTimeframe()} is on. */
	@ConfigItem(
		keyName = LAST_TIMEFRAME_KEY,
		name = "",
		description = "",
		hidden = true
	)
	default Timeframe lastTimeframe()
	{
		return Timeframe.ONE_DAY;
	}

	@ConfigItem(
		keyName = CHART_TYPE_KEY,
		name = "Chart type",
		description = "Line or candlestick chart. Also changed, and remembered, with the buttons above the chart.",
		position = 3
	)
	default ChartType chartType()
	{
		return ChartType.LINE;
	}

	@ConfigItem(
		keyName = "buyColor",
		name = "Buy colour",
		description = "Colour of the buy price: its line, tag, volume bars, rising candles and positive profit",
		position = 4
	)
	default Color buyColor()
	{
		return new Color(Palette.BUY);
	}

	@ConfigItem(
		keyName = "sellColor",
		name = "Sell colour",
		description = "Colour of the sell price: its line, tag, volume bars, falling candles and negative profit",
		position = 5
	)
	default Color sellColor()
	{
		return new Color(Palette.SELL);
	}

	@ConfigItem(
		keyName = "showVolume",
		name = "Show volume bars",
		description = "Show the traded volume below the price chart",
		position = 6
	)
	default boolean showVolume()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOfferLine",
		name = "Show offer price line",
		description = "Show the price entered on the GE offer screen as a line on the chart",
		position = 7
	)
	default boolean showOfferLine()
	{
		return true;
	}

	@ConfigItem(
		keyName = "crossCursor",
		name = "Cross cursor on chart",
		description = "Show a cross cursor while the mouse is over the chart",
		position = 8
	)
	default boolean crossCursor()
	{
		return true;
	}

	@ConfigItem(
		keyName = "snapCrosshairToCoins",
		name = "Snap crosshair to whole coins",
		description = "The crosshair's price line jumps from one whole coin to the next, as GE prices have no fractions."
			+ " Most visible on cheap items.",
		position = 9
	)
	default boolean snapCrosshairToCoins()
	{
		return true;
	}

	@ConfigItem(
		keyName = "timeFormat",
		name = "Time format",
		description = "12- or 24-hour clock on the chart. Automatic follows the language and region Java reports"
			+ " for this computer.",
		position = 10
	)
	default TimeFormat timeFormat()
	{
		return TimeFormat.AUTOMATIC;
	}
}
