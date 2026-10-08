package com.marketlens.ui;

import com.marketlens.chart.ChartViewport;
import com.marketlens.price.Timeframe;
import com.marketlens.price.TimeseriesPoint;
import java.awt.Rectangle;
import java.util.List;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.Setter;

/**
 * What the price window is showing: item, timeframe, zoom and hover.
 * Shared by the widget window, the chart overlay (client thread) and mouse input (AWT thread).
 */
@Singleton
public class ChartState
{
	private static final int MIN_VISIBLE_BUCKETS = 6;

	@Getter
	private final ChartViewport viewport = new ChartViewport();
	@Getter
	private volatile int itemId = -1;
	@Getter
	private volatile Timeframe timeframe = Timeframe.ONE_DAY;
	/** Plot area in canvas coordinates, published by the renderer every frame for mouse input. */
	@Getter
	@Setter
	private volatile Rectangle plotBounds;
	@Getter
	private volatile int hoverX = -1;
	@Getter
	private volatile int hoverY = -1;
	/** Bucket width of the applied series, in seconds. */
	@Getter
	private volatile long bucketSeconds = 300;

	private List<TimeseriesPoint> appliedPoints;

	public boolean isOpen()
	{
		return itemId > 0;
	}

	public void open(int itemId, Timeframe timeframe)
	{
		this.itemId = itemId;
		this.timeframe = timeframe;
		appliedPoints = null;
	}

	public void close()
	{
		itemId = -1;
		appliedPoints = null;
		plotBounds = null;
		clearHover();
	}

	public void setTimeframe(Timeframe timeframe)
	{
		this.timeframe = timeframe;
		appliedPoints = null;
	}

	public void setHover(int x, int y)
	{
		hoverX = x;
		hoverY = y;
	}

	public void clearHover()
	{
		setHover(-1, -1);
	}

	/** Points the viewport at new series data: a fresh view for a new series, a range update for a refresh. Client thread. */
	public void sync(List<TimeseriesPoint> points)
	{
		if (points == appliedPoints || points.isEmpty())
		{
			return;
		}

		long step = bucketSeconds(points);
		bucketSeconds = step;
		long first = points.get(0).getTimestamp();
		long last = points.get(points.size() - 1).getTimestamp();
		if (appliedPoints == null)
		{
			viewport.setData(first, last, step * MIN_VISIBLE_BUCKETS, timeframe.getInitialWindowSeconds());
		}
		else
		{
			viewport.updateRange(first, last);
		}
		appliedPoints = points;
	}

	/** Bucket width of a series, taken as the smallest gap between consecutive points. */
	private static long bucketSeconds(List<TimeseriesPoint> points)
	{
		long step = Long.MAX_VALUE;
		for (int i = 1; i < points.size(); i++)
		{
			long gap = points.get(i).getTimestamp() - points.get(i - 1).getTimestamp();
			if (gap > 0 && gap < step)
			{
				step = gap;
			}
		}
		return step == Long.MAX_VALUE ? 300 : step;
	}
}
