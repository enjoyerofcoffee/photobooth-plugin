package com.photobooth.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;

/**
 * Software rasterizer: orthographic projection, z-buffer, Gouraud (per-vertex colour) shading.
 * Has no Swing dependency, so it can also render off-screen (e.g. for screenshot export).
 * Reuses scratch buffers between calls, so it is not thread-safe: use one instance per thread.
 */
final class ModelRenderer
{
    // Flip if the model looks mirrored (e.g. weapon in the wrong hand). Not yet verified against in-game.
    private static final boolean MIRROR_X = false;
    private static final float EDGE_EPSILON = -1e-4f;
    private static final double FILL_FRACTION = 0.9; // model's bounding sphere fills 90% of the height at zoom 1

    private float[] depth = new float[0];
    private float[] screen = new float[0]; // projected x, y, z per vertex

    /** Clears {@code target} to transparent, then draws {@code model} into it. Target must be TYPE_INT_ARGB. */
    void render(ModelSnapshot model, double yaw, double pitch, double zoom, BufferedImage target)
    {
        if (target.getType() != BufferedImage.TYPE_INT_ARGB)
        {
            throw new IllegalArgumentException("target must be TYPE_INT_ARGB");
        }

        int width = target.getWidth();
        int height = target.getHeight();
        int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
        int pixelCount = width * height;

        if (depth.length < pixelCount)
        {
            depth = new float[pixelCount];
        }
        if (screen.length < model.vertices.length)
        {
            screen = new float[model.vertices.length];
        }

        Arrays.fill(pixels, 0);
        Arrays.fill(depth, 0, pixelCount, Float.POSITIVE_INFINITY);

        project(model, yaw, pitch, zoom, width, height);

        int[] faces = model.faces;
        int[] colors = model.colors;
        for (int i = 0; i < faces.length; i += 3)
        {
            rasterize(faces[i], faces[i + 1], faces[i + 2], colors[i], colors[i + 1], colors[i + 2],
                    pixels, width, height);
        }
    }

    private void project(ModelSnapshot model, double yaw, double pitch, double zoom, int width, int height)
    {
        float scale = (float) (height * FILL_FRACTION / (2 * model.radius) * zoom);
        float cosYaw = (float) Math.cos(yaw);
        float sinYaw = (float) Math.sin(yaw);
        float cosPitch = (float) Math.cos(pitch);
        float sinPitch = (float) Math.sin(pitch);
        float halfW = width / 2f;
        float halfH = height / 2f;

        float[] v = model.vertices;
        for (int i = 0; i < v.length; i += 3)
        {
            float x = v[i];
            float y = v[i + 1]; // OSRS y points down, same as screen y
            float z = v[i + 2];

            // Yaw: rotate around the vertical axis
            float x1 = x * cosYaw - z * sinYaw;
            float z1 = x * sinYaw + z * cosYaw;

            // Pitch: rotate around the horizontal axis
            float y2 = y * cosPitch - z1 * sinPitch;
            float z2 = y * sinPitch + z1 * cosPitch;

            screen[i] = halfW + (MIRROR_X ? -x1 : x1) * scale;
            screen[i + 1] = halfH + y2 * scale;
            screen[i + 2] = z2; // smaller = closer to the viewer
        }
    }

    private void rasterize(int a, int b, int c, int colA, int colB, int colC, int[] pixels, int width, int height)
    {
        float x0 = screen[a * 3], y0 = screen[a * 3 + 1], z0 = screen[a * 3 + 2];
        float x1 = screen[b * 3], y1 = screen[b * 3 + 1], z1 = screen[b * 3 + 2];
        float x2 = screen[c * 3], y2 = screen[c * 3 + 1], z2 = screen[c * 3 + 2];

        float area = (x1 - x0) * (y2 - y0) - (y1 - y0) * (x2 - x0);
        if (Math.abs(area) < 1e-6f)
        {
            return; // degenerate or edge-on
        }
        float invArea = 1f / area;

        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        if (minX > maxX || minY > maxY)
        {
            return;
        }

        int r0 = (colA >> 16) & 0xFF, g0 = (colA >> 8) & 0xFF, b0 = colA & 0xFF;
        int r1 = (colB >> 16) & 0xFF, g1 = (colB >> 8) & 0xFF, b1 = colB & 0xFF;
        int r2 = (colC >> 16) & 0xFF, g2 = (colC >> 8) & 0xFF, b2 = colC & 0xFF;

        for (int py = minY; py <= maxY; py++)
        {
            float pyc = py + 0.5f;
            int row = py * width;

            for (int px = minX; px <= maxX; px++)
            {
                float pxc = px + 0.5f;

                // Barycentric weights. Dividing by the signed area handles either winding order.
                float w0 = ((x2 - x1) * (pyc - y1) - (y2 - y1) * (pxc - x1)) * invArea;
                if (w0 < EDGE_EPSILON)
                {
                    continue;
                }
                float w1 = ((x0 - x2) * (pyc - y2) - (y0 - y2) * (pxc - x2)) * invArea;
                if (w1 < EDGE_EPSILON)
                {
                    continue;
                }
                float w2 = 1f - w0 - w1;
                if (w2 < EDGE_EPSILON)
                {
                    continue;
                }

                int idx = row + px;
                float z = w0 * z0 + w1 * z1 + w2 * z2;
                if (z >= depth[idx])
                {
                    continue;
                }
                depth[idx] = z;

                int r = clampByte(w0 * r0 + w1 * r1 + w2 * r2);
                int g = clampByte(w0 * g0 + w1 * g1 + w2 * g2);
                int bl = clampByte(w0 * b0 + w1 * b1 + w2 * b2);
                pixels[idx] = 0xFF000000 | (r << 16) | (g << 8) | bl;
            }
        }
    }

    private static int clampByte(float v)
    {
        return Math.max(0, Math.min(255, (int) v));
    }
}