package com.photobooth.lookup;

import com.photobooth.contract.Appearance;

/**
 * Outcome of a lookup. The UI switches on {@link Status}; the renderer only ever receives
 * {@link #getAppearance()}, never this wrapper, so it stays unaware of where data came from.
 */
public final class LookupResult
{
	public enum Status
	{
		/** Found in the API database. */
		FROM_API,
		/** Not in the API (or API down) but the player is near us; captured in-game. */
		CAPTURED_IN_GAME,
		/** Not in the API and not currently loaded near us. */
		NOT_FOUND,
		/** Input rejected before any lookup. */
		INVALID_NAME,
		/** Not logged in, so in-game capture can't run and the API missed or is off. */
		NOT_LOGGED_IN
	}

	private final Status status;
	private final Appearance appearance;
	private final String message;

	private LookupResult(Status status, Appearance appearance, String message)
	{
		this.status = status;
		this.appearance = appearance;
		this.message = message;
	}

	public static LookupResult found(Status status, Appearance appearance, String message)
	{
		return new LookupResult(status, appearance, message);
	}

	public static LookupResult missing(Status status, String message)
	{
		return new LookupResult(status, null, message);
	}

	public Status getStatus()
	{
		return status;
	}

	/** Null unless status is FROM_API or CAPTURED_IN_GAME. */
	public Appearance getAppearance()
	{
		return appearance;
	}

	/** Short user-facing explanation (e.g. "API unavailable, captured in-game"). */
	public String getMessage()
	{
		return message;
	}

	public boolean hasAppearance()
	{
		return appearance != null;
	}
}
