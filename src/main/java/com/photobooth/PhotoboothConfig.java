package com.photobooth;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PhotoboothConfig.GROUP)
public interface PhotoboothConfig extends Config
{
	String GROUP = "photobooth";

	@ConfigSection(
		name = "Appearance API",
		description = "Connection to the Photobooth appearance database",
		position = 0
	)
	String apiSection = "api";

	@ConfigItem(
		keyName = "apiBaseUrl",
		name = "API base URL",
		description = "HTTPS base URL of the Photobooth API, e.g. https://api.example.com. Leave blank to use in-game capture only.",
		section = apiSection,
		position = 0
	)
	default String apiBaseUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "apiKey",
		name = "API key",
		description = "Optional per-user key issued by the Photobooth API. Stored in your RuneLite profile, never bundled in the plugin.",
		secret = true,
		section = apiSection,
		position = 1
	)
	default String apiKey()
	{
		return "";
	}

	@ConfigItem(
		keyName = "contributeCaptures",
		name = "Upload in-game captures",
		description = "When a player is found near you but not in the database, upload their appearance so others can look them up.",
		section = apiSection,
		position = 2
	)
	default boolean contributeCaptures()
	{
		return true;
	}

	@ConfigItem(
		keyName = "cacheMinutes",
		name = "Cache lookups (minutes)",
		description = "How long a looked-up appearance is reused before asking the API again.",
		position = 3
	)
	default int cacheMinutes()
	{
		return 10;
	}
}
