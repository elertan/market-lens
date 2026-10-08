package com.marketlens.chart;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** How the price series is drawn. */
@Getter
@RequiredArgsConstructor
public enum ChartType
{
	LINE("Line"),
	CANDLES("Candles");

	private final String label;

	@Override
	public String toString()
	{
		return label;
	}
}
