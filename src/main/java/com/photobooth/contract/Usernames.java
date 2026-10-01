package com.photobooth.contract;

import java.util.Locale;

/**
 * Username normalisation shared by the plugin and the API.
 * <p>
 * OSRS treats spaces, underscores, hyphens and non-breaking spaces as the same character in
 * display names, and names are case-insensitive. The API must apply exactly the same rule so
 * that "Zezima", "zezima " and "ze_zima" style variants all hit the same database row.
 * Mirrors RuneLite's {@code Text.toJagexName} + {@code Text.standardize}, but without the
 * RuneLite dependency so the server can port it verbatim.
 */
public final class Usernames
{
	/** OSRS display names are at most 12 characters. */
	public static final int MAX_LENGTH = 12;

	private Usernames()
	{
	}

	/**
	 * @return the normalised key, e.g. {@code "Iron_Man-1"} becomes {@code "iron man 1"},
	 * or an empty string if nothing usable remains.
	 */
	public static String normalize(String raw)
	{
		if (raw == null)
		{
			return "";
		}
		return raw
			.replaceAll("[ _\\-]", " ")
			.replaceAll("[^\\x20-\\x7E]", "")
			.replaceAll(" +", " ")
			.trim()
			.toLowerCase(Locale.ROOT);
	}

	/** Cheap sanity check before we hit the network or scan the scene. */
	public static boolean isPlausible(String normalized)
	{
		return normalized != null
			&& !normalized.isEmpty()
			&& normalized.length() <= MAX_LENGTH
			&& normalized.matches("[a-z0-9 ]+");
	}
}
