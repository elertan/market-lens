package com.marketlens.ui;

import com.marketlens.MarketLensConfig;
import com.marketlens.chart.AxisTicks;
import com.marketlens.chart.Candle;
import com.marketlens.chart.Candles;
import com.marketlens.chart.ChartType;
import com.marketlens.chart.ChartScale;
import com.marketlens.chart.Downsample;
import com.marketlens.chart.LabelLayout;
import com.marketlens.chart.PriceFormat;
import com.marketlens.ge.GeOffer;
import com.marketlens.price.LatestPrice;
import com.marketlens.price.PriceService;
import com.marketlens.price.TimeseriesPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.FontManager;

/**
 * Draws the price chart into a given area: grid, axes, volume bars, high/low lines or candlesticks, live price
 * tags, the price chosen on the GE offer screen and a TradingView-style crosshair. Shared by the normal and the expanded chart, so both look the same at any size.
 * The geometry lives in {@link ChartScale}; this class only draws. When buckets would be drawn closer than a few
 * pixels apart, they are merged first ({@link Downsample}), so dense series stay readable. Client thread only.
 */
@Singleton
public class ChartRenderer
{
	private static final int INSET = 4;
	private static final int PRICE_AXIS_WIDTH = 50;
	private static final int TIME_AXIS_HEIGHT = 16;
	private static final int LEGEND_HEIGHT = 16;
	private static final int MIN_PX_PER_PRICE_TICK = 24;
	private static final int MIN_PX_PER_TIME_TICK = 64;
	private static final int MIN_PX_PER_DOT = 5;
	/** Below this spacing, neighbouring buckets are merged so the lines stay readable (see {@link Downsample}). */
	private static final int MIN_PX_PER_POINT = 2;
	/** Candles need room for a visible body, so they are merged until they are at least this far apart. */
	private static final int MIN_PX_PER_CANDLE = 5;
	/** Candle body width as a fraction of the space per candle. */
	private static final double CANDLE_BODY_FRACTION = 0.6;
	private static final int DOT_SIZE = 3;

	private static final Stroke LINE = new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
	private static final Stroke THIN = new BasicStroke(1f);
	private static final Stroke DASHED = new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
		10f, new float[]{3f, 3f}, 0f);

	private final ChartState state;
	private final PriceService prices;
	private final GeOffer offer;
	private final MarketLensConfig config;
	private final ZoneId zone = ZoneId.systemDefault();
	/** Pixel bounds of the digits in the chart font; measured once, used to centre labels. */
	private Rectangle2D digitBounds;
	/** Last merge result, reused until the series or the interval changes. */
	private List<TimeseriesPoint> mergedSource;
	private long mergedInterval;
	private List<TimeseriesPoint> merged;
	/** Last candle build, reused until the series or the interval changes; candlePoints match candles by index. */
	private List<TimeseriesPoint> candleSource;
	private long candleInterval;
	private List<Candle> candles;
	private List<TimeseriesPoint> candlePoints;

	@Inject
	ChartRenderer(ChartState state, PriceService prices, GeOffer offer, MarketLensConfig config)
	{
		this.state = state;
		this.prices = prices;
		this.offer = offer;
		this.config = config;
	}

	/** Draws the chart for the current item and timeframe into {@code area} (canvas coordinates). */
	public void render(Graphics2D graphics, Rectangle area)
	{
		PriceService.Series series = prices.getSeries(state.getItemId(), state.getTimeframe());
		state.sync(series.getPoints());

		Rectangle plot = new Rectangle(area.x + INSET, area.y + LEGEND_HEIGHT,
			area.width - PRICE_AXIS_WIDTH - INSET, area.height - LEGEND_HEIGHT - TIME_AXIS_HEIGHT);
		state.setPlotBounds(plot);

		Graphics2D g = (Graphics2D) graphics.create();
		try
		{
			Font font = FontManager.getRunescapeSmallFont();
			g.setFont(font);
			if (digitBounds == null)
			{
				digitBounds = font.createGlyphVector(g.getFontRenderContext(), "0123456789").getVisualBounds();
			}
			ChartText text = new ChartText(g, digitBounds);

			if (series.getPoints().isEmpty())
			{
				text.drawCentered(statusText(series.getStatus()), area.x + area.width / 2, area.y + area.height / 2,
					Palette.TEXT_ORANGE);
				return;
			}

			double start = state.getViewport().getStart();
			double end = state.getViewport().getEnd();
			boolean candleMode = state.getChartType() == ChartType.CANDLES;
			long interval = Downsample.interval(state.getBucketSeconds(), (end - start) / plot.width,
				candleMode ? MIN_PX_PER_CANDLE : MIN_PX_PER_POINT);
			List<Candle> candleList = candleMode ? candlesAt(series.getPoints(), interval) : null;
			List<TimeseriesPoint> points = candleMode ? candlePoints : pointsAt(series.getPoints(), interval);
			if (points.isEmpty())
			{
				text.drawCentered(statusText(PriceService.Status.READY), area.x + area.width / 2,
					area.y + area.height / 2, Palette.TEXT_ORANGE);
				return;
			}

			LatestPrice latest = prices.getLatest(state.getItemId());
			Long offerPrice = offer.priceFor(state.getItemId());
			ChartScale scale = new ChartScale(points, start, end, interval, plot,
				latest == null || latest.getHigh() == null ? null : latest.getHigh().doubleValue(),
				latest == null || latest.getLow() == null ? null : latest.getLow().doubleValue(),
				offerPrice == null ? null : offerPrice.doubleValue());
			new Frame(g, text, area, scale, points, candleList, interval, latest, offerPrice).draw();
		}
		finally
		{
			g.dispose();
		}
	}

	/** The series at {@code interval}: the raw points, or merged ones when the raw buckets are too dense. */
	private List<TimeseriesPoint> pointsAt(List<TimeseriesPoint> points, long interval)
	{
		if (interval == state.getBucketSeconds())
		{
			return points;
		}
		if (points != mergedSource || interval != mergedInterval)
		{
			merged = Downsample.merge(points, interval);
			mergedSource = points;
			mergedInterval = interval;
		}
		return merged;
	}

	/** Candles for the series at {@code interval}; also fills {@link #candlePoints}. */
	private List<Candle> candlesAt(List<TimeseriesPoint> points, long interval)
	{
		if (points != candleSource || interval != candleInterval)
		{
			candles = Candles.build(points, interval);
			candlePoints = candles.stream().map(Candle::asPoint).collect(Collectors.toList());
			candleSource = points;
			candleInterval = interval;
		}
		return candles;
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

	/** One frame of chart drawing, in back-to-front order. */
	private final class Frame
	{
		private final Graphics2D g;
		private final ChartText text;
		private final Rectangle area;
		private final ChartScale scale;
		private final Rectangle plot;
		private final List<TimeseriesPoint> points;
		/** The candles behind {@link #points} in candle mode, by the same index; null for the line chart. */
		private final List<Candle> candles;
		/** Seconds per point: the series' bucket size, or the merged interval. */
		private final long interval;
		private final LatestPrice latest;
		/** Price chosen on the GE offer screen, or null. */
		private final Long offerPrice;

		Frame(Graphics2D g, ChartText text, Rectangle area, ChartScale scale, List<TimeseriesPoint> points,
			List<Candle> candles, long interval, LatestPrice latest, Long offerPrice)
		{
			this.g = g;
			this.text = text;
			this.area = area;
			this.scale = scale;
			this.plot = scale.getPlot();
			this.points = points;
			this.candles = candles;
			this.interval = interval;
			this.latest = latest;
			this.offerPrice = offerPrice;
		}

		void draw()
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			List<Tag> tags = layoutTags();
			drawPriceGrid(tags);
			drawTimeGrid();

			Shape clip = g.getClip();
			g.clip(plot);
			drawVolume();
			if (candles != null)
			{
				drawCandles();
			}
			else
			{
				drawLine(TimeseriesPoint::getAvgHighPrice, Palette.BUY_COLOR);
				drawLine(TimeseriesPoint::getAvgLowPrice, Palette.SELL_COLOR);
			}
			g.setClip(clip);

			drawTags(tags);
			int hovered = drawCrosshair();
			drawLegend(hovered >= 0 ? hovered : scale.getLast());
		}

		/** Candlesticks: a 1-px wick from high to low and a body from open to close, green when it closed higher. */
		private void drawCandles()
		{
			int body = Math.max(1, (int) (scale.bucketWidth() * CANDLE_BODY_FRACTION));
			if (body % 2 == 0)
			{
				body--;  // odd width keeps the wick in the middle
			}
			// Crisp pixels: no anti-aliasing for the boxes and wicks.
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g.setStroke(THIN);
			for (int i = scale.getFirst(); i <= scale.getLast(); i++)
			{
				Candle c = candles.get(i);
				int x = scale.x(c.getTimestamp());
				int top = scale.y(Math.max(c.getOpen(), c.getClose()));
				int bottom = scale.y(Math.min(c.getOpen(), c.getClose()));
				g.setColor(c.isUp() ? Palette.BUY_COLOR : Palette.SELL_COLOR);
				g.drawLine(x, scale.y(c.getHigh()), x, scale.y(c.getLow()));
				g.fillRect(x - body / 2, top, body, Math.max(1, bottom - top + 1));
			}
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		}

		/** Horizontal grid + right axis labels, leaving out labels that a price tag would cover. */
		private void drawPriceGrid(List<Tag> tags)
		{
			Rectangle pane = scale.getPricePane();
			List<Double> ticks = AxisTicks.prices(scale.getMin(), scale.getMax(), Math.max(2, pane.height / MIN_PX_PER_PRICE_TICK));
			double step = ticks.size() > 1 ? ticks.get(1) - ticks.get(0) : 1;
			int labelX = plot.x + plot.width + 4;
			for (double tick : ticks)
			{
				int y = scale.y(tick);
				g.setColor(Palette.GRID);
				g.drawLine(plot.x, y, plot.x + plot.width, y);
				if (tags.stream().noneMatch(tag -> Math.abs(tag.labelY - y) < text.labelHeight()))
				{
					text.draw(PriceFormat.axis(tick, step), labelX, text.baselineFor(y), Palette.AXIS_TEXT);
				}
			}
		}

		/** Vertical grid + bottom axis labels. */
		private void drawTimeGrid()
		{
			double start = scale.getStart();
			double end = scale.getEnd();
			long step = AxisTicks.timeStep(start, end, Math.max(2, plot.width / MIN_PX_PER_TIME_TICK));
			int labelY = plot.y + plot.height + text.ascent() + 3;
			for (long tick : AxisTicks.times(start, end, step, zone))
			{
				int x = scale.x(tick);
				g.setColor(Palette.GRID);
				g.drawLine(x, plot.y, x, plot.y + plot.height);

				String label = AxisTicks.timeLabel(tick, step, zone);
				int labelX = x - text.width(label) / 2;
				if (labelX >= area.x && labelX + text.width(label) <= plot.x + plot.width)
				{
					text.draw(label, labelX, labelY, Palette.AXIS_TEXT);
				}
			}
		}

		/** Stacked bars: instant-buy volume at the bottom, instant-sell volume on top. */
		private void drawVolume()
		{
			Rectangle pane = scale.getVolumePane();
			int barWidth = Math.max(1, (int) scale.bucketWidth() - 1);
			int bottom = pane.y + pane.height;
			for (int i = scale.getFirst(); i <= scale.getLast(); i++)
			{
				TimeseriesPoint p = points.get(i);
				int x = scale.x(p.getTimestamp()) - barWidth / 2;
				int buyHeight = scale.volumeHeight(p.getHighPriceVolume());
				int sellHeight = scale.volumeHeight(p.getLowPriceVolume());
				g.setColor(Palette.BUY_VOLUME);
				g.fillRect(x, bottom - buyHeight, barWidth, buyHeight);
				g.setColor(Palette.SELL_VOLUME);
				g.fillRect(x, bottom - buyHeight - sellHeight, barWidth, sellHeight);
			}
		}

		/**
		 * Polyline through the bucket averages. Buckets without trades are skipped, so the line connects
		 * the surrounding prices; it also reaches back/forward past the view edges so it never starts mid-plot.
		 * Points get a dot once they are far enough apart to tell apart.
		 */
		private void drawLine(Function<TimeseriesPoint, Double> price, Color color)
		{
			int from = scale.getFirst();
			while (from > 0 && price.apply(points.get(from)) == null)
			{
				from--;
			}
			int to = scale.getLast();
			while (to < points.size() - 1 && price.apply(points.get(to)) == null)
			{
				to++;
			}

			boolean dots = scale.bucketWidth() >= MIN_PX_PER_DOT;
			Path2D path = new Path2D.Double();
			boolean started = false;
			g.setColor(color);
			for (int i = from; i <= to; i++)
			{
				TimeseriesPoint p = points.get(i);
				Double value = price.apply(p);
				if (value == null)
				{
					continue;
				}
				int px = scale.x(p.getTimestamp());
				int py = scale.y(value);
				if (started)
				{
					path.lineTo(px, py);
				}
				else
				{
					path.moveTo(px, py);
					started = true;
				}
				if (dots)
				{
					g.fillOval(px - DOT_SIZE / 2, py - DOT_SIZE / 2, DOT_SIZE, DOT_SIZE);
				}
			}
			g.setStroke(LINE);
			g.draw(path);
		}

		/** Live prices and the chosen offer price as tags on the price axis, moved apart where they would overlap. */
		private List<Tag> layoutTags()
		{
			List<Tag> tags = new ArrayList<>();
			if (latest != null)
			{
				addTag(tags, latest.getHigh(), Palette.BUY_COLOR, Palette.BUY_TAG_LINE);
				addTag(tags, latest.getLow(), Palette.SELL_COLOR, Palette.SELL_TAG_LINE);
			}
			addTag(tags, offerPrice, Palette.OFFER_COLOR, Palette.OFFER_TAG_LINE);

			Rectangle pane = scale.getPricePane();
			int[] lineYs = tags.stream().mapToInt(tag -> tag.lineY).toArray();
			int[] labelYs = LabelLayout.separate(lineYs, text.labelHeight(), pane.y, pane.y + pane.height);
			for (int i = 0; i < tags.size(); i++)
			{
				tags.get(i).labelY = labelYs[i];
			}
			return tags;
		}

		private void addTag(List<Tag> tags, Long price, Color color, Color lineColor)
		{
			if (price != null && price >= scale.getMin() && price <= scale.getMax())
			{
				tags.add(new Tag(price, color, lineColor, scale.y(price)));
			}
		}

		/** Dashed line at the exact price, coloured label at its (possibly moved) position on the axis. */
		private void drawTags(List<Tag> tags)
		{
			g.setStroke(DASHED);
			for (Tag tag : tags)
			{
				g.setColor(tag.lineColor);
				g.drawLine(plot.x, tag.lineY, plot.x + plot.width, tag.lineY);
			}
			for (Tag tag : tags)
			{
				text.drawBox(PriceFormat.axis(tag.price, 1), plot.x + plot.width + 1, tag.labelY, PRICE_AXIS_WIDTH - 2,
					tag.color, Color.BLACK);
			}
		}

		/** Crosshair snapped to the nearest point. Returns that point's index, or -1 when not hovering. */
		private int drawCrosshair()
		{
			int mx = state.getHoverX();
			int my = state.getHoverY();
			if (!plot.contains(mx, my))
			{
				return -1;
			}

			int index = scale.nearestIndex(scale.timeAt(mx));
			TimeseriesPoint nearest = points.get(index);
			int x = scale.x(nearest.getTimestamp());
			g.setStroke(DASHED);
			g.setColor(Palette.CROSSHAIR);
			g.drawLine(x, plot.y, x, plot.y + plot.height);
			if (scale.getPricePane().contains(mx, my))
			{
				// GE prices are whole coins, so by default the line snaps to the nearest one instead of a fraction.
				double price = scale.priceAt(my);
				int y = my;
				if (config.snapCrosshairToCoins())
				{
					price = Math.round(price);
					y = scale.y(price);
				}
				g.drawLine(plot.x, y, plot.x + plot.width, y);
				text.drawBox(PriceFormat.axis(price, 1), plot.x + plot.width + 1, y, PRICE_AXIS_WIDTH - 2,
					Palette.LABEL_BACKGROUND, Palette.TEXT);
			}

			String time = AxisTicks.crosshairLabel(nearest.getTimestamp(), interval, zone);
			int w = text.width(time) + 8;
			int boxX = Math.max(area.x, Math.min(x - w / 2, plot.x + plot.width - w));
			int boxY = plot.y + plot.height + 1;
			int boxHeight = TIME_AXIS_HEIGHT - 2;
			g.setColor(Palette.LABEL_BACKGROUND);
			g.fillRect(boxX, boxY, w, boxHeight);
			text.draw(time, boxX + 4, text.baselineFor(boxY + boxHeight / 2), Palette.TEXT);
			return index;
		}

		/**
		 * Readout for the hovered or newest point: "High 828,861  Low 808,000  Vol 16" for lines,
		 * "O 137  H 139  L 134  C 136  Vol 16" for candles (close coloured by direction).
		 */
		private void drawLegend(int index)
		{
			TimeseriesPoint p = points.get(index);
			int x = area.x + INSET;
			int y = area.y + text.ascent() + 3;
			if (candles != null)
			{
				Candle c = candles.get(index);
				x = legendItem("O ", c.getOpen(), Palette.TEXT, x, y);
				x = legendItem("H ", c.getHigh(), Palette.TEXT, x, y);
				x = legendItem("L ", c.getLow(), Palette.TEXT, x, y);
				x = legendItem("C ", c.getClose(), c.isUp() ? Palette.BUY_COLOR : Palette.SELL_COLOR, x, y);
			}
			else
			{
				x = legendItem("High ", p.getAvgHighPrice(), Palette.BUY_COLOR, x, y);
				x = legendItem("Low ", p.getAvgLowPrice(), Palette.SELL_COLOR, x, y);
			}
			text.draw("Vol ", x, y, Palette.TEXT_MUTED);
			text.draw(PriceFormat.exact(p.totalVolume()), x + text.width("Vol "), y, Palette.TEXT);
		}

		private int legendItem(String label, Double value, Color color, int x, int y)
		{
			text.draw(label, x, y, Palette.TEXT_MUTED);
			x += text.width(label);
			String formatted = value == null ? "-" : PriceFormat.exact(Math.round(value));
			text.draw(formatted, x, y, color);
			return x + text.width(formatted) + 10;
		}
	}

	/** A live-price tag: its dashed line sits at the exact price, its label may be moved to avoid overlap. */
	private static final class Tag
	{
		private final long price;
		private final Color color;
		private final Color lineColor;
		private final int lineY;
		private int labelY;

		Tag(long price, Color color, Color lineColor, int lineY)
		{
			this.price = price;
			this.color = color;
			this.lineColor = lineColor;
			this.lineY = lineY;
		}
	}
}
