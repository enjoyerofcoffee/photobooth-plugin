package com.photobooth.render;

/** Converts OSRS face colours to 24-bit RGB. */
final class JagexColours
{
    // Mirrors the in-game brightness setting (0.6 = brightest, 0.9 = darkest)
    private static final double BRIGHTNESS = 0.7;

    // The client offsets hue and saturation by half a step so values sit in the middle of each bucket
    private static final double HUE_OFFSET = 0.5 / 64.0;
    private static final double SATURATION_OFFSET = 0.5 / 8.0;

    private JagexColours()
    {
    }

    /**
     * @param colour   packed 16-bit HSL (6 bits hue, 3 saturation, 7 lightness), or for textured
     *                 faces a plain 0-127 lightness value
     * @param textured whether the face is textured; textures aren't rendered, so these become grey
     */
    static int toRgb(int colour, boolean textured)
    {
        return textured ? lightnessToGray(colour) : hslToRgb(colour);
    }

    private static int hslToRgb(int hsl)
    {
        hsl &= 0xFFFF;
        double hue = ((hsl >> 10) & 0x3F) / 64.0 + HUE_OFFSET;
        double sat = ((hsl >> 7) & 0x07) / 8.0 + SATURATION_OFFSET;
        double lum = (hsl & 0x7F) / 128.0;

        if (lum == 0)
        {
            return pack(0, 0, 0);
        }

        double q = lum < 0.5 ? lum * (1 + sat) : lum + sat - lum * sat;
        double p = 2 * lum - q;
        return pack(
                channel(p, q, hue + 1.0 / 3.0),
                channel(p, q, hue),
                channel(p, q, hue - 1.0 / 3.0));
    }

    private static int lightnessToGray(int lightness)
    {
        double l = Math.max(0, Math.min(127, lightness)) / 128.0;
        return pack(l, l, l);
    }

    /** Standard HSL-to-RGB helper for one channel. */
    private static double channel(double p, double q, double t)
    {
        if (t < 0)
        {
            t += 1;
        }
        if (t > 1)
        {
            t -= 1;
        }
        if (6 * t < 1)
        {
            return p + (q - p) * 6 * t;
        }
        if (2 * t < 1)
        {
            return q;
        }
        if (3 * t < 2)
        {
            return p + (q - p) * (2.0 / 3.0 - t) * 6;
        }
        return p;
    }

    private static int pack(double r, double g, double b)
    {
        return (to8(r) << 16) | (to8(g) << 8) | to8(b);
    }

    private static int to8(double v)
    {
        return Math.min(255, (int) (Math.pow(Math.max(0, v), BRIGHTNESS) * 256));
    }
}