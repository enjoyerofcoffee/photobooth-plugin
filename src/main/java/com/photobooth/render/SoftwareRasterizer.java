package com.photobooth.render;

import com.photobooth.model.MeshSnapshot;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Small software rasterizer: draws a {@link MeshSnapshot} into an ARGB image with a z-buffer
 * and per-vertex (Gouraud) colour interpolation, using an orthographic camera that orbits the
 * model. Pure Java, stateless, safe to call on any thread; the viewer calls it on the EDT only
 * when the view changes, never per game frame.
 * <p>
 * Cost is roughly proportional to faces + covered pixels: a few ms for a ~2-4k face player at
 * sidebar size.
 */
public final class SoftwareRasterizer
{
	/** Everything that decides what the picture looks like. */
	public static final class Camera
	{
		/** Rotation around the vertical axis, radians. */
		public final double yaw;
		/** Tilt, radians; positive looks down on the model. */
		public final double pitch;
		/** 1.0 = model fills ~90% of the frame. */
		public final double zoom;

		public Camera(double yaw, double pitch, double zoom)
		{
			this.yaw = yaw;
			this.pitch = pitch;
			this.zoom = zoom;
		}
	}

	/**
	 * Depth range (model units; a player is ~200 tall) inside which render priority decides
	 * which face is in front instead of depth. Amulets sit a unit or two behind the chest
	 * surface. Raise if details still sink into armour; lower if faces start showing through
	 * things clearly in front of them.
	 */
	static final float PRIORITY_DEPTH_TOLERANCE = 4f;

	private SoftwareRasterizer()
	{
	}

	/**
	 * @param background ARGB background; use 0 for a transparent PNG.
	 */
	public static BufferedImage render(MeshSnapshot mesh, int width, int height, Camera camera, int background)
	{
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
		Arrays.fill(pixels, background);
		if (mesh == null || mesh.getFaceCount() == 0)
		{
			return image;
		}

		float[] depth = new float[width * height];
		Arrays.fill(depth, Float.POSITIVE_INFINITY);
		byte[] depthPriority = new byte[width * height];

		// ---- transform vertices to screen space ----
		int n = mesh.getVertexCount();
		float[] sx = new float[n];
		float[] sy = new float[n];
		float[] sz = new float[n];

		double fit = Math.min(0.9 * height / mesh.getHeight(), 0.9 * width / (2.0 * mesh.getRadiusXZ()));
		double scale = fit * camera.zoom;
		double cosY = Math.cos(camera.yaw);
		double sinY = Math.sin(camera.yaw);
		double cosP = Math.cos(camera.pitch);
		double sinP = Math.sin(camera.pitch);
		float[] vx = mesh.vx();
		float[] vy = mesh.vy();
		float[] vz = mesh.vz();
		for (int i = 0; i < n; i++)
		{
			double x = vx[i] - mesh.getCenterX();
			double y = vy[i] - mesh.getCenterY();
			double z = vz[i] - mesh.getCenterZ();

			double x1 = x * cosY + z * sinY;
			double z1 = -x * sinY + z * cosY;

			// +Y is down in model space, so a positive pitch lifts the far side.
			double y2 = y * cosP - z1 * sinP;
			double z2 = y * sinP + z1 * cosP;

			sx[i] = (float) (width / 2.0 + x1 * scale);
			sy[i] = (float) (height / 2.0 + y2 * scale);
			sz[i] = (float) z2;
		}

		int[] f1 = mesh.f1();
		int[] f2 = mesh.f2();
		int[] f3 = mesh.f3();
		int[] c1 = mesh.c1();
		int[] c2 = mesh.c2();
		int[] c3 = mesh.c3();
		byte[] alphaBytes = mesh.transparency();
		byte[] priorities = mesh.priorities();

		// ---- opaque pass (writes depth), collect transparent faces ----
		List<Integer> transparent = new ArrayList<>();
		for (int f = 0; f < mesh.getFaceCount(); f++)
		{
			if (c3[f] == MeshSnapshot.HIDDEN)
			{
				continue;
			}
			int t = alphaBytes == null ? 0 : alphaBytes[f] & 0xFF;
			if (t != 0)
			{
				transparent.add(f);
				continue;
			}
			drawFace(f, f1, f2, f3, c1, c2, c3, sx, sy, sz, pixels, depth, depthPriority,
				priorityOf(priorities, f), width, height, 1f, true);
		}

		// ---- transparent pass: back to front, blend, no depth writes ----
		transparent.sort((a, b) -> Float.compare(avgDepth(b, f1, f2, f3, sz), avgDepth(a, f1, f2, f3, sz)));
		for (int f : transparent)
		{
			float alpha = 1f - (alphaBytes[f] & 0xFF) / 255f;
			if (alpha <= 0f)
			{
				continue;
			}
			drawFace(f, f1, f2, f3, c1, c2, c3, sx, sy, sz, pixels, depth, depthPriority,
				priorityOf(priorities, f), width, height, alpha, false);
		}
		return image;
	}

	private static byte priorityOf(byte[] priorities, int face)
	{
		return priorities == null ? 0 : priorities[face];
	}

	/**
	 * Depth test with the game's render priority: a clearly closer face always wins, a clearly
	 * farther one always loses, and within PRIORITY_DEPTH_TOLERANCE the higher priority wins.
	 */
	private static boolean passesDepth(float z, byte priority, float storedZ, byte storedPriority)
	{
		if (z < storedZ - PRIORITY_DEPTH_TOLERANCE)
		{
			return true;
		}
		if (z > storedZ + PRIORITY_DEPTH_TOLERANCE)
		{
			return false;
		}
		if (priority != storedPriority)
		{
			return priority > storedPriority;
		}
		return z < storedZ;
	}

	private static float avgDepth(int f, int[] f1, int[] f2, int[] f3, float[] sz)
	{
		return (sz[f1[f]] + sz[f2[f]] + sz[f3[f]]) / 3f;
	}

	private static void drawFace(int f, int[] f1, int[] f2, int[] f3, int[] c1, int[] c2, int[] c3,
		float[] sx, float[] sy, float[] sz, int[] pixels, float[] depth, byte[] depthPriority,
		byte priority, int width, int height, float alpha, boolean writeDepth)
	{
		int a = f1[f];
		int b = f2[f];
		int c = f3[f];
		if (a < 0 || b < 0 || c < 0 || a >= sx.length || b >= sx.length || c >= sx.length)
		{
			return;
		}

		int rgbA = HslPalette.toRgb(c1[f]);
		int rgbB;
		int rgbC;
		if (c3[f] == MeshSnapshot.FLAT_SHADED)
		{
			rgbB = rgbA;
			rgbC = rgbA;
		}
		else
		{
			rgbB = HslPalette.toRgb(c2[f]);
			rgbC = HslPalette.toRgb(c3[f]);
		}

		float x0 = sx[a], y0 = sy[a], z0 = sz[a];
		float x1 = sx[b], y1 = sy[b], z1 = sz[b];
		float x2 = sx[c], y2 = sy[c], z2 = sz[c];

		float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
		if (Math.abs(area) < 1e-6f)
		{
			return;
		}

		int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
		int maxX = Math.min(width - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
		int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
		int maxY = Math.min(height - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
		if (minX > maxX || minY > maxY)
		{
			return;
		}

		float invArea = 1f / area;
		int rA = rgbA >> 16 & 0xFF, gA = rgbA >> 8 & 0xFF, bA = rgbA & 0xFF;
		int rB = rgbB >> 16 & 0xFF, gB = rgbB >> 8 & 0xFF, bB = rgbB & 0xFF;
		int rC = rgbC >> 16 & 0xFF, gC = rgbC >> 8 & 0xFF, bC = rgbC & 0xFF;

		for (int py = minY; py <= maxY; py++)
		{
			float cy = py + 0.5f;
			for (int px = minX; px <= maxX; px++)
			{
				float cx = px + 0.5f;
				// Barycentric weights via edge functions; sign-normalised by the area so both
				// windings are drawn (models contain single-sided faces like capes).
				float w0 = ((x1 - cx) * (y2 - cy) - (x2 - cx) * (y1 - cy)) * invArea;
				float w1 = ((x2 - cx) * (y0 - cy) - (x0 - cx) * (y2 - cy)) * invArea;
				float w2 = 1f - w0 - w1;
				if (w0 < 0f || w1 < 0f || w2 < 0f)
				{
					continue;
				}

				float z = w0 * z0 + w1 * z1 + w2 * z2;
				int idx = py * width + px;
				if (!passesDepth(z, priority, depth[idx], depthPriority[idx]))
				{
					continue;
				}

				int r = (int) (w0 * rA + w1 * rB + w2 * rC);
				int g = (int) (w0 * gA + w1 * gB + w2 * gC);
				int bl = (int) (w0 * bA + w1 * bB + w2 * bC);

				if (alpha >= 1f)
				{
					pixels[idx] = 0xFF000000 | r << 16 | g << 8 | bl;
				}
				else
				{
					int dst = pixels[idx];
					float inv = 1f - alpha;
					int dr = (int) (r * alpha + (dst >> 16 & 0xFF) * inv);
					int dg = (int) (g * alpha + (dst >> 8 & 0xFF) * inv);
					int db = (int) (bl * alpha + (dst & 0xFF) * inv);
					int da = Math.max(dst >>> 24, (int) (alpha * 255));
					pixels[idx] = da << 24 | dr << 16 | dg << 8 | db;
				}
				if (writeDepth)
				{
					depth[idx] = z;
					depthPriority[idx] = priority;
				}
			}
		}
	}
}
