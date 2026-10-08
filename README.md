# <img src="icon.png" alt="" width="40"> Market Lens

A RuneLite plugin that adds real-time Grand Exchange price charts to the GE offer screen.

When you set up a buy or sell offer, a **Market Lens** button (the price-chart icon) appears right of the Confirm button. Click it to open the Market Lens window on top of the GE:

- Instant buy and sell prices, with the time of the last trade
- Margin, GE tax (2%, capped at 5M, exempt items handled) and profit per item
- Buy limit and 1-hour trade volume
- A price chart with high/low lines, volume bars, a crosshair and live price tags
- The price you chose on the GE offer screen, as a line on the chart in the GE's own price colour, so you can see where your offer sits
- Dense data is merged into larger intervals (15m, 1h, ...) using volume-weighted averages, so the lines stay readable without losing accuracy; zoom in for every single bucket
- Timeframes: 1H, 6H, 24H, 1W, 1M, 6M, 1Y
- Line or candlestick chart, switched with the buttons next to the timeframes and remembered in the plugin settings
- An expand button that opens the chart over most of the game screen

Chart controls: scroll to zoom (hold Shift or Ctrl to zoom around the cursor), drag to pan, double-click to reset. Press Esc or the back arrow to close the window; in the expanded chart, Esc or the close button returns to the normal view.

Price data comes from the [OSRS Wiki real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices).

## Development

```bash
./gradlew run     # starts RuneLite in developer mode with the plugin loaded
./gradlew test    # unit tests for price data, caching, GE tax and chart maths
```

On macOS with Java 17 or newer, RuneLite needs one extra JVM option to start:

```bash
JAVA_TOOL_OPTIONS="--add-opens=java.desktop/com.apple.eawt=ALL-UNNAMED" ./gradlew run
```

The icons are generated pixel art. To change them, edit [`tools/make_icons.py`](tools/make_icons.py) and run:

```bash
python3 tools/make_icons.py
```

Other icon designs that were considered (candlesticks, price tags, an eye, ...) are kept in
[`tools/icon_concepts.py`](tools/icon_concepts.py); their PNGs and an overview sheet are in
[`tools/icon-concepts/`](tools/icon-concepts/). Regenerate them with `python3 tools/icon_concepts.py`.

| Package | Contents |
| --- | --- |
| `price` | Wiki API client, cache/refresh policy, GE tax |
| `chart` | Pure chart maths: viewport (zoom/pan), scale, axis ticks, label layout, formatting |
| `ge` | Adds the Market Lens button to the GE offer setup screen |
| `ui` | Normal and expanded windows, chart renderer and overlays, mouse input, shared chart state |
