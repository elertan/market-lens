package com.marketlens.chart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the price series is drawn. */
@Getter
@RequiredArgsConstructor
public enum ChartType
{
	LINE("Line", "Linechart"),
	CANDLES("Candles", "Candlechart");

	private final String label;
	/** Name in the toggle button's menu option, e.g. "Show Linechart". */
	private final String menuName;

	@Override
	public String toString()
	{
		return label;
	}
}
