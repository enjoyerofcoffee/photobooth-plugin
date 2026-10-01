package com.photobooth.render;

/**
 * Converts OSRS 16-bit packed HSL colours (6 bits hue, 3 bits saturation, 7 bits luminance,
 * the same packing as {@code net.runelite.api.JagexColor}) to RGB.
 * <p>
 * This is the classic client palette algorithm. The brightness exponent mirrors the in-game
 * brightness slider; 0.7 is a reasonable default and is easy to tune if colours look washed out
 * or too dark compared to the game.
 */
public final class HslPalette
{
	public static final double DEFAULT_BRIGHTNESS = 0.7;

	private static final int[] DEFAULT = build(DEFAULT_BRIGHTNESS);

	private HslPalette()
	{
	}

	/** @return 0xRRGGBB for a packed HSL value (only the low 16 bits are used). */
	public static int toRgb(int hsl)
	{
		return DEFAULT[hsl & 0xFFFF];
	}

	static int[] build(double brightness)
	{
		int[] palette = new int[65536];
		int index = 0;
		for (int hs = 0; hs < 512; hs++)
		{
			double hue = (hs >> 3) / 64.0 + 0.0078125;
			double sat = (hs & 7) / 8.0 + 0.0625;
			for (int l = 0; l < 128; l++)
			{
				double lum = l / 128.0;
				double r = lum;
				double g = lum;
				double b = lum;
				double q = lum < 0.5 ? lum * (1.0 + sat) : lum + sat - lum * sat;
				double p = 2.0 * lum - q;
				double hr = hue + 1.0 / 3.0;
				if (hr > 1.0)
				{
					hr -= 1.0;
				}
				double hb = hue - 1.0 / 3.0;
				if (hb < 0.0)
				{
					hb += 1.0;
				}
				r = channel(p, q, hr);
				g = channel(p, q, hue);
				b = channel(p, q, hb);

				palette[index++] = gamma(r, brightness) << 16 | gamma(g, brightness) << 8 | gamma(b, brightness);
			}
		}
		return palette;
	}

	private static double channel(double p, double q, double t)
	{
		if (6.0 * t < 1.0)
		{
			return p + (q - p) * 6.0 * t;
		}
		if (2.0 * t < 1.0)
		{
			return q;
		}
		if (3.0 * t < 2.0)
		{
			return p + (q - p) * (2.0 / 3.0 - t) * 6.0;
		}
		return p;
	}

	private static int gamma(double v, double brightness)
	{
		int c = (int) (Math.pow(Math.max(0.0, v), brightness) * 256.0);
		return Math.min(255, Math.max(0, c));
	}
}
