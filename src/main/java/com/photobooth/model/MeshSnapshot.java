package com.photobooth.model;

import java.util.Arrays;

/**
 * Immutable copy of a model's geometry and lit face colours.
 * <p>
 * The client's Model objects are shared and mutated by the game every frame, so we copy what
 * we need on the client thread and then render from this snapshot on the Swing EDT without
 * ever touching the client again. Pure Java: no RuneLite types.
 * <p>
 * Coordinate system is OSRS model space: +X right, +Y DOWN (feet near 0, head negative),
 * Z depth.
 */
public final class MeshSnapshot
{
	/** faceColors3 value meaning "flat shaded: use colour 1 for all three vertices". */
	public static final int FLAT_SHADED = -1;
	/** faceColors3 value meaning "face is hidden, do not draw". */
	public static final int HIDDEN = -2;

	private final int vertexCount;
	private final float[] vx;
	private final float[] vy;
	private final float[] vz;

	private final int faceCount;
	private final int[] f1;
	private final int[] f2;
	private final int[] f3;
	private final int[] c1;
	private final int[] c2;
	private final int[] c3;
	/** Per-face transparency, 0 = opaque, 255 = fully transparent. May be null (all opaque). */
	private final byte[] transparency;
	/**
	 * Per-face render priority 0-11 from Model#getFaceRenderPriorities. When two faces are at
	 * about the same depth, the game draws the higher priority on top; amulets and armour trims
	 * rely on this. May be null (all priority 0).
	 */
	private final byte[] priorities;

	private final float centerX;
	private final float centerY;
	private final float centerZ;
	private final float height;
	private final float radiusXZ;

	public MeshSnapshot(int vertexCount, float[] vx, float[] vy, float[] vz,
		int faceCount, int[] f1, int[] f2, int[] f3,
		int[] c1, int[] c2, int[] c3, byte[] transparency, byte[] priorities)
	{
		this.vertexCount = vertexCount;
		this.vx = Arrays.copyOf(vx, vertexCount);
		this.vy = Arrays.copyOf(vy, vertexCount);
		this.vz = Arrays.copyOf(vz, vertexCount);
		this.faceCount = faceCount;
		this.f1 = Arrays.copyOf(f1, faceCount);
		this.f2 = Arrays.copyOf(f2, faceCount);
		this.f3 = Arrays.copyOf(f3, faceCount);
		this.c1 = Arrays.copyOf(c1, faceCount);
		this.c2 = Arrays.copyOf(c2, faceCount);
		this.c3 = Arrays.copyOf(c3, faceCount);
		this.transparency = transparency == null ? null : Arrays.copyOf(transparency, faceCount);
		this.priorities = priorities == null ? null : Arrays.copyOf(priorities, faceCount);

		float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
		float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
		for (int i = 0; i < vertexCount; i++)
		{
			minX = Math.min(minX, this.vx[i]);
			maxX = Math.max(maxX, this.vx[i]);
			minY = Math.min(minY, this.vy[i]);
			maxY = Math.max(maxY, this.vy[i]);
			minZ = Math.min(minZ, this.vz[i]);
			maxZ = Math.max(maxZ, this.vz[i]);
		}
		if (vertexCount == 0)
		{
			minX = maxX = minY = maxY = minZ = maxZ = 0;
		}
		this.centerX = (minX + maxX) / 2f;
		this.centerY = (minY + maxY) / 2f;
		this.centerZ = (minZ + maxZ) / 2f;
		this.height = Math.max(1f, maxY - minY);

		float r = 1f;
		for (int i = 0; i < vertexCount; i++)
		{
			float dx = this.vx[i] - centerX;
			float dz = this.vz[i] - centerZ;
			r = Math.max(r, (float) Math.sqrt(dx * dx + dz * dz));
		}
		this.radiusXZ = r;
	}

	public int getVertexCount()
	{
		return vertexCount;
	}

	public int getFaceCount()
	{
		return faceCount;
	}

	// Package-private-style raw accessors: the rasterizer reads these in tight loops, so we
	// hand out the internal arrays. Callers must treat them as read-only.

	public float[] vx()
	{
		return vx;
	}

	public float[] vy()
	{
		return vy;
	}

	public float[] vz()
	{
		return vz;
	}

	public int[] f1()
	{
		return f1;
	}

	public int[] f2()
	{
		return f2;
	}

	public int[] f3()
	{
		return f3;
	}

	public int[] c1()
	{
		return c1;
	}

	public int[] c2()
	{
		return c2;
	}

	public int[] c3()
	{
		return c3;
	}

	public byte[] transparency()
	{
		return transparency;
	}

	public byte[] priorities()
	{
		return priorities;
	}

	public float getCenterX()
	{
		return centerX;
	}

	public float getCenterY()
	{
		return centerY;
	}

	public float getCenterZ()
	{
		return centerZ;
	}

	public float getHeight()
	{
		return height;
	}

	/** Largest horizontal distance of any vertex from the centre; used to fit while rotating. */
	public float getRadiusXZ()
	{
		return radiusXZ;
	}
}
