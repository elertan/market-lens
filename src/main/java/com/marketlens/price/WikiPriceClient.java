package com.marketlens.price;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Thin async client for the OSRS Wiki real-time prices API (v2).
 * Callbacks run on the OkHttp thread pool, never on the client thread.
 */
@Slf4j
@Singleton
public class WikiPriceClient
{
	private static final HttpUrl BASE_URL = HttpUrl.get("https://prices.runescape.wiki/api/v2/osrs/");
	// The wiki blocks default Java user agents and asks for a descriptive one.
	private static final String USER_AGENT = "market-lens RuneLite plugin";

	private static final Type LATEST_TYPE = new TypeToken<Map<Integer, LatestPrice>>() {}.getType();
	private static final Type HOURLY_TYPE = new TypeToken<Map<Integer, HourlyVolume>>() {}.getType();
	private static final Type TIMESERIES_TYPE = new TypeToken<List<TimeseriesPoint>>() {}.getType();
	private static final Type MAPPING_TYPE = new TypeToken<List<ItemMapping>>() {}.getType();

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	WikiPriceClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public void fetchLatest(Consumer<Map<Integer, LatestPrice>> onSuccess, Runnable onFailure)
	{
		get(url("latest"), r -> parseData(gson, r, LATEST_TYPE), onSuccess, onFailure);
	}

	public void fetchHourly(Consumer<Map<Integer, HourlyVolume>> onSuccess, Runnable onFailure)
	{
		get(url("1h"), r -> parseData(gson, r, HOURLY_TYPE), onSuccess, onFailure);
	}

	public void fetchMapping(Consumer<Map<Integer, ItemMapping>> onSuccess, Runnable onFailure)
	{
		get(url("mapping"), r -> parseMapping(gson, r), onSuccess, onFailure);
	}

	public void fetchTimeseries(int itemId, Timeframe timeframe, Consumer<List<TimeseriesPoint>> onSuccess, Runnable onFailure)
	{
		HttpUrl url = url("timeseries").newBuilder()
			.addQueryParameter("id", Integer.toString(itemId))
			.addQueryParameter("lookback", timeframe.getLookback())
			.build();
		get(url, r -> parseData(gson, r, TIMESERIES_TYPE), onSuccess, onFailure);
	}

	/** Unwraps the {@code {"data": ...}} envelope used by every endpoint except /mapping. */
	static <T> T parseData(Gson gson, Reader reader, Type dataType)
	{
		JsonObject root = gson.fromJson(reader, JsonObject.class);
		return gson.fromJson(root.get("data"), dataType);
	}

	static Map<Integer, ItemMapping> parseMapping(Gson gson, Reader reader)
	{
		List<ItemMapping> list = gson.fromJson(reader, MAPPING_TYPE);
		Map<Integer, ItemMapping> byId = new HashMap<>(list.size() * 2);
		for (ItemMapping m : list)
		{
			byId.put(m.getId(), m);
		}
		return byId;
	}

	private static HttpUrl url(String endpoint)
	{
		return BASE_URL.newBuilder().addPathSegment(endpoint).build();
	}

	private <T> void get(HttpUrl url, Function<Reader, T> parser, Consumer<T> onSuccess, Runnable onFailure)
	{
		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Request to {} failed", url, e);
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Request to {} returned HTTP {}", url, response.code());
						onFailure.run();
						return;
					}
					onSuccess.accept(parser.apply(body.charStream()));
				}
				catch (RuntimeException e)
				{
					log.debug("Unable to parse response from {}", url, e);
					onFailure.run();
				}
			}
		});
	}
}
