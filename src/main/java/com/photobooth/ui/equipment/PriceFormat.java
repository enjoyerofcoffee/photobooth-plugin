package com.photobooth.ui.equipment;

import java.awt.Color;
import net.runelite.client.util.QuantityFormatter;

/**
 * OSRS-style price text: "9,669", "151K", "2.5M", coloured like in-game stack sizes
 * (yellow below 100K, white from 100K, green from 10M).
 */
public final class PriceFormat
{
	static final Color YELLOW = new Color(255, 255, 0);
	static final Color WHITE = Color.WHITE;
	static final Color GREEN = new Color(0, 255, 128);

	private PriceFormat()
	{
	}

	public static String text(long coins)
	{
		return coins <= 0 ? "-" : QuantityFormatter.quantityToStackSize(coins);
	}

	public static Color color(long coins)
	{
		if (coins >= 10_000_000L)
		{
			return GREEN;
		}
		if (coins >= 100_000L)
		{
			return WHITE;
		}
		return YELLOW;
	}
}
