package com.photobooth.lookup;

import com.photobooth.PhotoboothConfig;
import com.photobooth.api.PhotoboothApiClient;
import com.photobooth.capture.PlayerAppearanceCapture;
import com.photobooth.contract.Appearance;
import com.photobooth.contract.Usernames;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;

/**
 * Orchestrates the search flows. Knows nothing about Swing or rendering.
 * <pre>
 *   API hit                       -> FROM_API            (render only, no writes)
 *   API 404, player nearby        -> CAPTURED_IN_GAME    (render + PUT to API if enabled)
 *   API down/off, player nearby   -> CAPTURED_IN_GAME    (render only, no upload)
 *   otherwise                     -> NOT_FOUND / NOT_LOGGED_IN
 * </pre>
 * Returned futures complete on OkHttp or client threads. Callers that touch Swing must
 * hop to the EDT themselves.
 */
@Slf4j
@Singleton
public class AppearanceLookupService
{
	private final PhotoboothApiClient api;
	private final PlayerAppearanceCapture capture;
	private final Client client;
	private final ClientThread clientThread;
	private final PhotoboothConfig config;

	private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

	@Inject
	AppearanceLookupService(PhotoboothApiClient api, PlayerAppearanceCapture capture, Client client,
		ClientThread clientThread, PhotoboothConfig config)
	{
		this.api = api;
		this.capture = capture;
		this.client = client;
		this.clientThread = clientThread;
		this.config = config;
	}

	public CompletableFuture<LookupResult> lookup(String rawName)
	{
		String name = Usernames.normalize(rawName);
		if (!Usernames.isPlausible(name))
		{
			return CompletableFuture.completedFuture(
				LookupResult.missing(LookupResult.Status.INVALID_NAME, "That isn't a valid OSRS name."));
		}

		CacheEntry cached = cache.get(name);
		if (cached != null && !cached.isExpired(cacheTtl()))
		{
			log.debug("Photobooth cache hit for {}", name);
			return CompletableFuture.completedFuture(cached.result);
		}

		if (!api.isConfigured())
		{
			return captureFallback(name, false, "No API configured");
		}

		return api.getAppearance(name)
			.handle((found, error) ->
			{
				if (error != null)
				{
					log.warn("Photobooth API lookup failed, falling back to in-game capture", unwrap(error));
					return captureFallback(name, false, "API unavailable");
				}
				if (found.isPresent())
				{
					LookupResult result = LookupResult.found(LookupResult.Status.FROM_API, found.get(),
						"Loaded from database (captured " + found.get().getCapturedAt() + ")");
					cache.put(name, new CacheEntry(result));
					return CompletableFuture.completedFuture(result);
				}
				return captureFallback(name, true, "Not in database");
			})
			.thenCompose(f -> f);
	}

	public void clearCache()
	{
		cache.clear();
	}

	/**
	 * @param uploadIfFound true only when the API positively said 404, so we never overwrite a
	 *                      row just because the API was temporarily unreachable.
	 */
	private CompletableFuture<LookupResult> captureFallback(String name, boolean uploadIfFound, String reason)
	{
		CompletableFuture<LookupResult> future = new CompletableFuture<>();

		// Scene reads MUST happen on the client thread.
		clientThread.invoke(() ->
		{
			try
			{
				if (client.getGameState() != GameState.LOGGED_IN)
				{
					future.complete(LookupResult.missing(LookupResult.Status.NOT_LOGGED_IN,
						reason + ". Log in and stand near the player to capture them."));
					return;
				}

				Optional<Appearance> captured = capture.captureNearby(name);
				if (captured.isEmpty())
				{
					future.complete(LookupResult.missing(LookupResult.Status.NOT_FOUND,
						reason + ", and the player isn't near you right now."));
					return;
				}

				Appearance appearance = captured.get();
				LookupResult result = LookupResult.found(LookupResult.Status.CAPTURED_IN_GAME, appearance,
					reason + ". Captured in-game.");
				cache.put(name, new CacheEntry(result));
				future.complete(result);

				if (uploadIfFound && config.contributeCaptures())
				{
					upload(appearance);
				}
			}
			catch (RuntimeException e)
			{
				future.completeExceptionally(e);
			}
		});
		return future;
	}

	/** Fire-and-forget. Rendering never waits on this. */
	private void upload(Appearance appearance)
	{
		api.putAppearance(appearance).whenComplete((ok, error) ->
		{
			if (error != null)
			{
				log.warn("Photobooth upload failed for {}", appearance.getNormalizedName(), unwrap(error));
			}
			else
			{
				log.debug("Photobooth uploaded appearance for {}", appearance.getNormalizedName());
			}
		});
	}

	private Duration cacheTtl()
	{
		return Duration.ofMinutes(Math.max(0, config.cacheMinutes()));
	}

	private static Throwable unwrap(Throwable t)
	{
		return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
	}

	private static final class CacheEntry
	{
		private final LookupResult result;
		private final Instant storedAt = Instant.now();

		CacheEntry(LookupResult result)
		{
			this.result = result;
		}

		boolean isExpired(Duration ttl)
		{
			return Instant.now().isAfter(storedAt.plus(ttl));
		}
	}
}
