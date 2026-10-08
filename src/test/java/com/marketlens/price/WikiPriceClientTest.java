package com.marketlens.price;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Parsing against payloads captured from the live v2 API. */
public class WikiPriceClientTest
{
	private final Gson gson = new Gson();

	@Test
	public void parsesLatest()
	{
		String json = "{\"data\":{\"4151\":{\"high\":828861,\"highTime\":1791445917,\"low\":808000,\"lowTime\":1791445847},"
			+ "\"2\":{\"high\":null,\"highTime\":null,\"low\":270,\"lowTime\":1791445000}}}";
		Map<Integer, LatestPrice> latest = WikiPriceClient.parseData(gson, new StringReader(json),
			new TypeToken<Map<Integer, LatestPrice>>() {}.getType());

		assertEquals(Long.valueOf(828_861), latest.get(4151).getHigh());
		assertEquals(Long.valueOf(808_000), latest.get(4151).getLow());
		assertNull(latest.get(2).getHigh());
	}

	@Test
	public void parsesTimeseriesWithGaps()
	{
		String json = "{\"data\":[{\"timestamp\":1791424200,\"avgHighPrice\":816043,\"avgLowPrice\":801554.2,"
			+ "\"highPriceVolume\":11,\"lowPriceVolume\":5},"
			+ "{\"timestamp\":1791424500,\"avgHighPrice\":null,\"avgLowPrice\":802000,\"highPriceVolume\":0,\"lowPriceVolume\":3}],"
			+ "\"itemId\":4151,\"timestep\":300}";
		List<TimeseriesPoint> points = WikiPriceClient.parseData(gson, new StringReader(json),
			new TypeToken<List<TimeseriesPoint>>() {}.getType());

		assertEquals(2, points.size());
		assertEquals(801_554.2, points.get(0).getAvgLowPrice(), 0.001);
		assertEquals(16, points.get(0).totalVolume());
		assertNull(points.get(1).getAvgHighPrice());
	}

	@Test
	public void parsesMapping()
	{
		String json = "[{\"examine\":\"A weapon from the abyss.\",\"id\":4151,\"members\":true,\"limit\":70,"
			+ "\"value\":120001,\"name\":\"Abyssal whip\"},{\"id\":2,\"name\":\"Cannonball\"}]";
		Map<Integer, ItemMapping> mapping = WikiPriceClient.parseMapping(gson, new StringReader(json));

		assertEquals("Abyssal whip", mapping.get(4151).getName());
		assertEquals(Integer.valueOf(70), mapping.get(4151).getLimit());
		assertNull(mapping.get(2).getLimit());
	}
}
