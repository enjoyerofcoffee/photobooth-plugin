package com.photobooth.api;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.photobooth.PhotoboothConfig;
import com.photobooth.contract.Appearance;
import com.photobooth.contract.Usernames;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Only class that talks HTTP. Everything is async: calls are enqueued on OkHttp's own
 * threads, so this is safe to call from any thread EXCEPT that you must never block
 * (future.join()) on the client thread or the Swing EDT.
 * <p>
 * Uses RuneLite's injected OkHttpClient and Gson, as the Plugin Hub requires.
 */
@Slf4j
@Singleton
public class PhotoboothApiClient
{
	private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
	private static final String API_KEY_HEADER = "X-Api-Key";

	private final OkHttpClient http;
	private final Gson gson;
	private final PhotoboothConfig config;

	@Inject
	PhotoboothApiClient(OkHttpClient http, Gson gson, PhotoboothConfig config)
	{
		this.http = http;
		this.gson = gson;
		this.config = config;
	}

	/** False when no base URL is configured; callers then skip straight to in-game capture. */
	public boolean isConfigured()
	{
		return baseUrl() != null;
	}

	/**
	 * GET /v1/appearances/{normalizedName}
	 *
	 * @return empty on 404, the validated appearance on 200, failed future otherwise.
	 */
	public CompletableFuture<Optional<Appearance>> getAppearance(String normalizedName)
	{
		CompletableFuture<Optional<Appearance>> future = new CompletableFuture<>();
		HttpUrl url = appearanceUrl(normalizedName);
		if (url == null)
		{
			future.completeExceptionally(new ApiException("API base URL not configured or invalid", -1));
			return future;
		}

		Request request = baseRequest(url).get().build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(new ApiException("Network error contacting API", e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody body = response.body())
				{
					if (response.code() == 404)
					{
						future.complete(Optional.empty());
						return;
					}
					if (!response.isSuccessful() || body == null)
					{
						future.completeExceptionally(new ApiException("API returned HTTP " + response.code(), response.code()));
						return;
					}
					Appearance appearance = gson.fromJson(body.charStream(), Appearance.class);
					List<String> problems = appearance == null ? List.of("empty body") : appearance.validate();
					if (!problems.isEmpty())
					{
						future.completeExceptionally(new ApiException("Invalid appearance from API: " + problems, response.code()));
						return;
					}
					future.complete(Optional.of(appearance));
				}
				catch (JsonParseException e)
				{
					future.completeExceptionally(new ApiException("Malformed JSON from API", e));
				}
				catch (RuntimeException e)
				{
					future.completeExceptionally(new ApiException("Unexpected error reading API response", e));
				}
			}
		});
		return future;
	}

	/**
	 * PUT /v1/appearances/{normalizedName} (idempotent upsert).
	 * Completes normally on 200/201/204.
	 */
	public CompletableFuture<Void> putAppearance(Appearance appearance)
	{
		CompletableFuture<Void> future = new CompletableFuture<>();
		HttpUrl url = appearanceUrl(appearance.getNormalizedName());
		if (url == null)
		{
			future.completeExceptionally(new ApiException("API base URL not configured or invalid", -1));
			return future;
		}

		RequestBody body = RequestBody.create(JSON, gson.toJson(appearance));
		Request request = baseRequest(url).put(body).build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				future.completeExceptionally(new ApiException("Network error uploading appearance", e));
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (ResponseBody ignored = response.body())
				{
					if (response.isSuccessful())
					{
						future.complete(null);
					}
					else
					{
						future.completeExceptionally(new ApiException("Upload failed: HTTP " + response.code(), response.code()));
					}
				}
			}
		});
		return future;
	}

	private Request.Builder baseRequest(HttpUrl url)
	{
		Request.Builder builder = new Request.Builder()
			.url(url)
			.header("Accept", "application/json");
		String key = config.apiKey();
		if (key != null && !key.isBlank())
		{
			builder.header(API_KEY_HEADER, key.trim());
		}
		return builder;
	}

	/** Builds {base}/v1/appearances/{name}; the name is path-encoded by HttpUrl, never concatenated. */
	private HttpUrl appearanceUrl(String normalizedName)
	{
		HttpUrl base = baseUrl();
		if (base == null || !Usernames.isPlausible(normalizedName))
		{
			return null;
		}
		return base.newBuilder()
			.addPathSegment("v1")
			.addPathSegment("appearances")
			.addPathSegment(normalizedName)
			.build();
	}

	/** Parses the configured base URL; HTTPS only (plain http allowed for localhost dev). */
	private HttpUrl baseUrl()
	{
		String raw = config.apiBaseUrl();
		if (raw == null || raw.isBlank())
		{
			return null;
		}
		HttpUrl url = HttpUrl.parse(raw.trim());
		if (url == null)
		{
			log.warn("Photobooth API base URL is not a valid URL");
			return null;
		}
		boolean local = "localhost".equals(url.host()) || "127.0.0.1".equals(url.host());
		if (!url.isHttps() && !local)
		{
			log.warn("Photobooth API base URL must use https");
			return null;
		}
		return url;
	}
}
