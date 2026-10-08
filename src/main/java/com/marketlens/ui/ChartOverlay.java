package com.marketlens.ui;

import com.marketlens.chart.AxisTicks;
import com.marketlens.chart.PriceFormat;
import com.marketlens.price.LatestPrice;
import com.marketlens.price.PriceService;
import com.marketlens.price.TimeseriesPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the price chart inside the window's chart area: grid, axes, volume bars, high/low lines,
 * last-price tags and a TradingView-style crosshair. Drawn right after the GE interface so game
 * menus still render on top.
 */
@Singleton
public class ChartOverlay extends Overlay
{
	private static final int INSET = 4;
	private static final int PRICE_AXIS_WIDTH = 50;
	private static final int TIME_AXIS_HEIGHT = 16;
	private static final int LEGEND_HEIGHT = 16;
	private static final int PANE_GAP = 4;
	private static final double VOLUME_FRACTION = 0.2;
	private static final double PRICE_PADDING = 0.08;
	private static final int MIN_PX_PER_PRICE_TICK = 32;
	private static final int MIN_PX_PER_TIME_TICK = 64;

	private static final Stroke LINE = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
		10f, new float[]{3f, 3f}, 0f);

	private final PriceWindow window;
	private final ChartState state;
	private final PriceService prices;
	private final ZoneId zone = ZoneId.systemDefault();

	@Inject
	ChartOverlay(PriceWindow window, ChartState state, PriceService prices)
	{
		this.window = window;
		this.state = state;
		this.prices = prices;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.MANUAL);
		drawAfterInterface(InterfaceID.GE_OFFERS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Rectangle area = window.getChartBounds();
		if (area == null)
		{
			state.setPlotBounds(null);
			return null;
		}

		PriceService.Series series = prices.getSeries(state.getItemId(), state.getTimeframe());
		state.sync(series.getPoints());

		Rectangle plot = new Rectangle(area.x + INSET, area.y + LEGEND_HEIGHT,
			area.width - PRICE_AXIS_WIDTH - INSET, area.height - LEGEND_HEIGHT - TIME_AXIS_HEIGHT);
		state.setPlotBounds(plot);

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			g.setFont(FontManager.getRunescapeSmallFont());
			if (series.getPoints().isEmpty())
			{
				drawCentered(g, area, statusText(series.getStatus()));
			}
			else
			{
				new Frame(g, area, plot, series.getPoints()).draw();
			}
		}
		finally
		{
			g.dispose();
		}
		return null;
	}

	private static String statusText(PriceService.Status status)
	{
		switch (status)
		{
			case LOADING:
				return "Loading prices...";
			case FAILED:
				return "Price data unavailable, retrying...";
			default:
				return "No trades in this period";
		}
	}

	private static void drawCentered(Graphics2D g, Rectangle area, String text)
	{
		FontMetrics fm = g.getFontMetrics();
		drawText(g, text, area.x + (area.width - fm.stringWidth(text)) / 2, area.y + area.height / 2, Palette.TEXT_ORANGE);
	}

	/** OSRS-style text: one-pixel black drop shadow. */
	private static void drawText(Graphics2D g, String text, int x, int y, Color color)
	{
		g.setColor(Color.BLACK);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/** One frame of chart drawing: holds the scales so the draw steps stay small. */
	private final class Frame
	{
		private final Graphics2D g;
		private final Rectangle area;
		private final Rectangle plot;
		private final Rectangle pricePane;
		private final Rectangle volumePane;
		private final List<TimeseriesPoint> points;
		private final long bucket = state.getBucketSeconds();
		private final double start = state.getViewport().getStart();
		private final double end = state.getViewport().getEnd();
		private final int first;
		private final int last;
		private double min = Double.MAX_VALUE;
		private double max = -Double.MAX_VALUE;
		private long maxVolume = 1;

		Frame(Graphics2D g, Rectangle area, Rectangle plot, List<TimeseriesPoint> points)
		{
			this.g = g;
			this.area = area;
			this.plot = plot;
			this.points = points;

			int volumeHeight = (int) (plot.height * VOLUME_FRACTION);
			pricePane = new Rectangle(plot.x, plot.y, plot.width, plot.height - volumeHeight - PANE_GAP);
			volumePane = new Rectangle(plot.x, plot.y + plot.height - volumeHeight, plot.width, volumeHeight);

			// Visible slice, one bucket of slack either side so lines run to the edges.
			int lo = 0;
			while (lo < points.size() - 1 && points.get(lo + 1).getTimestamp() < start)
			{
				lo++;
			}
			int hi = points.size() - 1;
			while (hi > lo && points.get(hi - 1).getTimestamp() > end)
			{
				hi--;
			}
			first = lo;
			last = hi;

			for (int i = first; i <= last; i++)
			{
				TimeseriesPoint p = points.get(i);
				include(p.getAvgHighPrice());
				include(p.getAvgLowPrice());
				maxVolume = Math.max(maxVolume, p.totalVolume());
			}
			if (min > max)
			{
				min = 0;
				max = 1;
			}
			double pad = Math.max(1, (max - min) * PRICE_PADDING);
			min -= pad;
			max += pad;
		}

		private void include(Double price)
		{
			if (price != null)
			{
				min = Math.min(min, price);
				max = Math.max(max, price);
			}
		}

		void draw()
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			drawPriceGrid();
			drawTimeGrid();

			Shape clip = g.getClip();
			g.clip(plot);
			drawVolume();
			drawLine(TimeseriesPoint::getAvgHighPrice, Palette.BUY_COLOR);
			drawLine(TimeseriesPoint::getAvgLowPrice, Palette.SELL_COLOR);
			g.setClip(clip);

			drawLastPriceTags();
			TimeseriesPoint hovered = drawCrosshair();
			drawLegend(hovered != null ? hovered : points.get(last));
		}

		private int x(double time)
		{
			return plot.x + (int) Math.round((time - start) / (end - start) * plot.width);
		}

		private int y(double price)
		{
			return pricePane.y + (int) Math.round((max - price) / (max - min) * pricePane.height);
		}

		private double priceAt(int y)
		{
			return max - (double) (y - pricePane.y) / pricePane.height * (max - min);
		}

		private double timeAt(int x)
		{
			return start + (double) (x - plot.x) / plot.width * (end - start);
		}

		/** Horizontal grid + right axis labels. */
		private void drawPriceGrid()
		{
			List<Double> ticks = AxisTicks.prices(min, max, Math.max(2, pricePane.height / MIN_PX_PER_PRICE_TICK));
			double step = ticks.size() > 1 ? ticks.get(1) - ticks.get(0) : 1;
			int labelX = plot.x + plot.width + 4;
			int ascent = g.getFontMetrics().getAscent();
			for (double tick : ticks)
			{
				int y = y(tick);
				g.setColor(Palette.GRID);
				g.drawLine(plot.x, y, plot.x + plot.width, y);
				drawText(g, PriceFormat.axis(tick, step), labelX, y + ascent / 2 - 1, Palette.AXIS_TEXT);
			}
		}

		/** Vertical grid + bottom axis labels. */
		private void drawTimeGrid()
		{
			long step = AxisTicks.timeStep(start, end, Math.max(2, plot.width / MIN_PX_PER_TIME_TICK));
			FontMetrics fm = g.getFontMetrics();
			int labelY = plot.y + plot.height + fm.getAscent() + 3;
			for (long tick : AxisTicks.times(start, end, step, zone))
			{
				int x = x(tick);
				g.setColor(Palette.GRID);
				g.drawLine(x, plot.y, x, plot.y + plot.height);

				String label = AxisTicks.timeLabel(tick, step, zone);
				int labelX = x - fm.stringWidth(label) / 2;
				if (labelX >= area.x && labelX + fm.stringWidth(label) <= plot.x + plot.width)
				{
					drawText(g, label, labelX, labelY, Palette.AXIS_TEXT);
				}
			}
		}

		/** Stacked bars: instant-buy volume at the bottom, instant-sell volume on top. */
		private void drawVolume()
		{
			int barWidth = Math.max(1, (int) (plot.width * bucket / (end - start)) - 1);
			int bottom = volumePane.y + volumePane.height;
			for (int i = first; i <= last; i++)
			{
				TimeseriesPoint p = points.get(i);
				int x = x(p.getTimestamp()) - barWidth / 2;
				int buyHeight = volumeHeight(p.getHighPriceVolume());
				int sellHeight = volumeHeight(p.getLowPriceVolume());
				g.setColor(Palette.BUY_VOLUME);
				g.fillRect(x, bottom - buyHeight, barWidth, buyHeight);
				g.setColor(Palette.SELL_VOLUME);
				g.fillRect(x, bottom - buyHeight - sellHeight, barWidth, sellHeight);
			}
		}

		private int volumeHeight(Long volume)
		{
			return volume == null ? 0 : (int) (volume * volumePane.height / maxVolume);
		}

		/** Polyline through the bucket averages; a bucket without trades breaks the line. */
		private void drawLine(Function<TimeseriesPoint, Double> price, Color color)
		{
			Path2D path = new Path2D.Double();
			boolean drawing = false;
			for (int i = first; i <= last; i++)
			{
				TimeseriesPoint p = points.get(i);
				Double value = price.apply(p);
				if (value == null)
				{
					drawing = false;
					continue;
				}
				if (drawing)
				{
					path.lineTo(x(p.getTimestamp()), y(value));
				}
				else
				{
					path.moveTo(x(p.getTimestamp()), y(value));
					drawing = true;
				}
			}
			g.setStroke(LINE);
			g.setColor(color);
			g.draw(path);
		}

		/** Live /latest prices as dashed lines with coloured tags on the price axis. */
		private void drawLastPriceTags()
		{
			LatestPrice latest = prices.getLatest(state.getItemId());
			if (latest == null)
			{
				return;
			}
			drawPriceTag(latest.getHigh(), Palette.BUY_COLOR);
			drawPriceTag(latest.getLow(), Palette.SELL_COLOR);
		}

		private void drawPriceTag(Long price, Color color)
		{
			if (price == null || price < min || price > max)
			{
				return;
			}
			int y = y(price);
			g.setStroke(DASHED);
			g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 140));
			g.drawLine(plot.x, y, plot.x + plot.width, y);
			drawAxisBox(PriceFormat.axis(price, 1), plot.x + plot.width + 1, y, color, Color.BLACK);
		}

		/** Crosshair snapped to the nearest bucket. Returns that bucket, or null when not hovering. */
		private TimeseriesPoint drawCrosshair()
		{
			int mx = state.getHoverX();
			int my = state.getHoverY();
			if (!plot.contains(mx, my))
			{
				return null;
			}

			TimeseriesPoint nearest = nearest(timeAt(mx));
			int x = x(nearest.getTimestamp());
			g.setStroke(DASHED);
			g.setColor(Palette.CROSSHAIR);
			g.drawLine(x, plot.y, x, plot.y + plot.height);
			if (pricePane.contains(mx, my))
			{
				g.drawLine(plot.x, my, plot.x + plot.width, my);
				drawAxisBox(PriceFormat.axis(priceAt(my), 1), plot.x + plot.width + 1, my,
					Palette.LABEL_BACKGROUND, Palette.TEXT);
			}

			String time = AxisTicks.crosshairLabel(nearest.getTimestamp(), bucket, zone);
			FontMetrics fm = g.getFontMetrics();
			int w = fm.stringWidth(time) + 8;
			int boxX = Math.max(area.x, Math.min(x - w / 2, plot.x + plot.width - w));
			int boxY = plot.y + plot.height + 1;
			g.setColor(Palette.LABEL_BACKGROUND);
			g.fillRect(boxX, boxY, w, TIME_AXIS_HEIGHT - 2);
			drawText(g, time, boxX + 4, boxY + fm.getAscent() + 1, Palette.TEXT);
			return nearest;
		}

		private TimeseriesPoint nearest(double time)
		{
			TimeseriesPoint best = points.get(first);
			for (int i = first + 1; i <= last; i++)
			{
				TimeseriesPoint p = points.get(i);
				if (Math.abs(p.getTimestamp() - time) < Math.abs(best.getTimestamp() - time))
				{
					best = p;
				}
			}
			return best;
		}

		private void drawAxisBox(String text, int x, int centerY, Color background, Color foreground)
		{
			FontMetrics fm = g.getFontMetrics();
			int h = fm.getAscent() + 4;
			g.setColor(background);
			g.fillRect(x, centerY - h / 2, PRICE_AXIS_WIDTH - 2, h);
			g.setColor(foreground);
			g.drawString(text, x + 3, centerY + fm.getAscent() / 2 - 1);
		}

		/** "High 828,861  Low 808,000  Vol 16" for the hovered or newest bucket. */
		private void drawLegend(TimeseriesPoint p)
		{
			int x = area.x + INSET;
			int y = area.y + g.getFontMetrics().getAscent() + 3;
			x = legendItem("High ", p.getAvgHighPrice(), Palette.BUY_COLOR, x, y);
			x = legendItem("Low ", p.getAvgLowPrice(), Palette.SELL_COLOR, x, y);
			drawText(g, "Vol ", x, y, Palette.TEXT_MUTED);
			x += g.getFontMetrics().stringWidth("Vol ");
			drawText(g, PriceFormat.exact(p.totalVolume()), x, y, Palette.TEXT);
		}

		private int legendItem(String label, Double value, Color color, int x, int y)
		{
			FontMetrics fm = g.getFontMetrics();
			drawText(g, label, x, y, Palette.TEXT_MUTED);
			x += fm.stringWidth(label);
			String text = value == null ? "-" : PriceFormat.exact(Math.round(value));
			drawText(g, text, x, y, color);
			return x + fm.stringWidth(text) + 10;
		}
	}
}
