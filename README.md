# <img src="src/main/resources/com/marketlens/ui/chart_icon.png" alt="" width="40"> Market Lens

A RuneLite plugin that adds real-time Grand Exchange price charts to the GE offer screen.

When you set up a buy or sell offer, a **Market Lens** button (the price-chart icon) appears right of the Confirm button. Click it to open the Market Lens window on top of the GE:

- Instant buy and sell prices, with the time of the last trade
- Margin, GE tax (2%, capped at 5M, exempt items handled) and profit per item
- Buy limit and 1-hour trade volume
- A price chart with high/low lines, volume bars, a crosshair and live price tags
- Timeframes: 1H, 6H, 24H, 1W, 1M, 6M, 1Y

Chart controls: scroll to zoom (hold Shift or Ctrl to zoom around the cursor), drag to pan, double-click to reset. Press Esc or the back arrow to close the window.

Price data comes from the [OSRS Wiki real-time prices API](https://oldschool.runescape.wiki/w/RuneScape:Real-time_Prices).

## Development

```bash
./gradlew run     # starts RuneLite in developer mode with the plugin loaded
./gradlew test    # unit tests for price parsing, GE tax and chart maths
```

| Package | Contents |
| --- | --- |
| `price` | Wiki API client, cache/refresh policy, GE tax |
| `chart` | Pure chart maths: viewport (zoom/pan), axis ticks, formatting |
| `ge` | Adds the Market Lens button to the GE offer setup screen |
| `ui` | Widget window, chart overlay, mouse input, shared chart state |
