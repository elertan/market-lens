package com.marketlens.chart;

/**
 * The visible time window of the chart, in epoch seconds. Pure maths, no rendering.
 * Mutated from the AWT thread (mouse input) and read from the client thread (rendering), hence synchronized.
 */
public final class ChartViewport
{
	private double dataStart;
	private double dataEnd;
	private double minSpan;
	private double initialWindow;
	private double start;
	private double end;

	/**
	 * Replaces the data range and resets the view.
	 *
	 * @param minSpan       smallest zoomable span (e.g. a few buckets)
	 * @param initialWindow span to show on reset, anchored to the end; 0 shows everything
	 */
	public synchronized void setData(long dataStart, long dataEnd, long minSpan, long initialWindow)
	{
		this.dataStart = dataStart;
		this.dataEnd = Math.max(dataEnd, dataStart + 1);
		this.minSpan = Math.max(1, Math.min(minSpan, this.dataEnd - dataStart));
		this.initialWindow = initialWindow;
		reset();
	}

	public synchronized void reset()
	{
		double span = dataEnd - dataStart;
		double window = initialWindow > 0 ? Math.max(minSpan, Math.min(initialWindow, span)) : span;
		end = dataEnd;
		start = dataEnd - window;
	}

	/** Zooms around {@code anchor}, keeping it at the same screen position. factor &lt; 1 zooms in. */
	public synchronized void zoom(double factor, double anchor)
	{
		double span = end - start;
		double newSpan = clamp(span * factor, minSpan, dataEnd - dataStart);
		double ratio = span <= 0 ? 1 : clamp((anchor - start) / span, 0, 1);
		start = anchor - ratio * newSpan;
		end = start + newSpan;
		keepInsideData();
	}

	/** Shifts the window by {@code seconds}; positive moves towards newer data. */
	public synchronized void pan(double seconds)
	{
		start += seconds;
		end += seconds;
		keepInsideData();
	}

	public synchronized double getStart()
	{
		return start;
	}

	public synchronized double getEnd()
	{
		return end;
	}

	private void keepInsideData()
	{
		double span = end - start;
		if (start < dataStart)
		{
			start = dataStart;
			end = start + span;
		}
		if (end > dataEnd)
		{
			end = dataEnd;
			start = end - span;
		}
	}

	private static double clamp(double v, double min, double max)
	{
		return Math.max(min, Math.min(max, v));
	}
}
