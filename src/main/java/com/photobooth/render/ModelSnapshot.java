package com.photobooth.modelviewer;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.runelite.api.Model;

/**
 * Immutable copy of a model's geometry and colours, centred on the origin.
 * Built on the client thread, rendered on the Swing EDT.
 *
 * <p>The arrays are package-private so the renderer can read them without accessor overhead in its
 * hot loop; nothing outside this package can reach them, and nothing inside mutates them.
 */
public final class ModelSnapshot
{
    // Sentinels the client stores in a face's third colour
    private static final int COLOR_FLAT = -1;   // whole face uses colour 1
    private static final int COLOR_HIDDEN = -2; // face isn't drawn
    private static final int INVISIBLE_ALPHA = 250; // 0 = opaque, 255 = fully transparent

    /** x, y, z per vertex, centred on the bounding-box centre. OSRS y points down. */
    final float[] vertices;
    /** Three vertex indices per face. */
    final int[] faces;
    /** Three 0xRRGGBB colours per face, one per corner. */
    final int[] colors;
    /** Half the bounding-box diagonal, at least 1. */
    final float radius;

    private ModelSnapshot(float[] vertices, int[] faces, int[] colors)
    {
        this.faces = faces;
        this.colors = colors;
        this.vertices = vertices;
        this.radius = centerOnOrigin(vertices);
    }

    public int getVertexCount()
    {
        return vertices.length / 3;
    }

    public int getFaceCount()
    {
        return faces.length / 3;
    }

    /** Client thread only. Returns null if there's nothing to draw. */
    @Nullable
    public static ModelSnapshot from(@Nullable Model model)
    {
        if (model == null)
        {
            return null;
        }

        // The client reuses buffers, so arrays can be longer than the counts: always loop to the count
        int vertexCount = model.getVerticesCount();
        int faceCount = model.getFaceCount();
        if (vertexCount <= 0 || faceCount <= 0)
        {
            return null;
        }

        float[] vertices = copyVertices(model, vertexCount);

        int[] i1 = model.getFaceIndices1();
        int[] i2 = model.getFaceIndices2();
        int[] i3 = model.getFaceIndices3();
        int[] c1 = model.getFaceColors1();
        int[] c2 = model.getFaceColors2();
        int[] c3 = model.getFaceColors3();
        byte[] transparencies = model.getFaceTransparencies(); // null when the model is fully opaque
        short[] textures = model.getFaceTextures();            // null when the model is untextured

        int[] faces = new int[faceCount * 3];
        int[] colors = new int[faceCount * 3];
        int n = 0;

        for (int f = 0; f < faceCount; f++)
        {
            if (c3[f] == COLOR_HIDDEN
                    || (transparencies != null && (transparencies[f] & 0xFF) >= INVISIBLE_ALPHA))
            {
                continue;
            }

            boolean flat = c3[f] == COLOR_FLAT;
            boolean textured = textures != null && textures[f] != -1;

            faces[n] = i1[f];
            faces[n + 1] = i2[f];
            faces[n + 2] = i3[f];
            colors[n] = JagexColours.toRgb(c1[f], textured);
            colors[n + 1] = flat ? colors[n] : JagexColours.toRgb(c2[f], textured);
            colors[n + 2] = flat ? colors[n] : JagexColours.toRgb(c3[f], textured);
            n += 3;
        }

        if (n == 0)
        {
            return null;
        }
        return new ModelSnapshot(vertices, Arrays.copyOf(faces, n), Arrays.copyOf(colors, n));
    }

    private static float[] copyVertices(Model model, int count)
    {
        float[] mx = model.getVerticesX();
        float[] my = model.getVerticesY();
        float[] mz = model.getVerticesZ();

        float[] out = new float[count * 3];
        for (int i = 0; i < count; i++)
        {
            out[i * 3] = mx[i];
            out[i * 3 + 1] = my[i];
            out[i * 3 + 2] = mz[i];
        }
        return out;
    }

    /** Translates vertices in place so the bounding box is centred on 0,0,0. Returns the bounding radius. */
    private static float centerOnOrigin(float[] vertices)
    {
        float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int i = 0; i < vertices.length; i++)
        {
            int axis = i % 3;
            min[axis] = Math.min(min[axis], vertices[i]);
            max[axis] = Math.max(max[axis], vertices[i]);
        }

        float[] center = new float[3];
        float diagonalSq = 0;
        for (int axis = 0; axis < 3; axis++)
        {
            center[axis] = (min[axis] + max[axis]) / 2f;
            float extent = max[axis] - min[axis];
            diagonalSq += extent * extent;
        }

        for (int i = 0; i < vertices.length; i++)
        {
            vertices[i] -= center[i % 3];
        }
        return Math.max(1f, (float) Math.sqrt(diagonalSq) / 2f);
    }
}