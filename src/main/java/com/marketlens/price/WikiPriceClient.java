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
import java.util.concurrent.TimeUnit;
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
	// The wiki blocks default Java user agents and asks for a descriptive one with a way to get in touch.
	private static final String USER_AGENT = "market-lens RuneLite plugin - https://github.com/elertan/market-lens";
	// The timeseries endpoint is sometimes slow to answer; RuneLite's default timeout gives up too early.
	private static final long READ_TIMEOUT_SECONDS = 30;

	private static final Type LATEST_TYPE = new TypeToken<Map<Integer, LatestPrice>>() {}.getType();
	private static final Type TIMESERIES_TYPE = new TypeToken<List<TimeseriesPoint>>() {}.getType();
	private static final Type MAPPING_TYPE = new TypeToken<List<ItemMapping>>() {}.getType();

	private final OkHttpClient http;
	private final Gson gson;

	@Inject
	WikiPriceClient(OkHttpClient http, Gson gson)
	{
		this.http = http.newBuilder().readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).build();
		this.gson = gson;
	}

	/** Live price of one item. Calls back with null if the item has no recent trades. */
	public void fetchLatest(int itemId, Consumer<LatestPrice> onSuccess, Runnable onFailure)
	{
		HttpUrl url = url("latest").newBuilder()
			.addQueryParameter("id", Integer.toString(itemId))
			.build();
		get(url, r -> WikiPriceClient.<Map<Integer, LatestPrice>>parseData(gson, r, LATEST_TYPE).get(itemId),
			onSuccess, onFailure);
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

	private static long elapsedMs(long startedNanos)
	{
		return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
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

		long started = System.nanoTime();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Request to {} failed after {} ms", url, elapsedMs(started), e);
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (!response.isSuccessful() || body == null)
					{
						log.debug("Request to {} returned HTTP {} after {} ms", url, response.code(), elapsedMs(started));
						onFailure.run();
						return;
					}
					T result = parser.apply(body.charStream());
					log.debug("Request to {} took {} ms", url, elapsedMs(started));
					onSuccess.accept(result);
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
