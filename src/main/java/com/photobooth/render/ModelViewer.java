package com.photobooth.modelviewer;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import javax.annotation.Nullable;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Mouse-orbitable view of a {@link ModelSnapshot}. EDT only. */
public class ModelViewer extends JPanel
{
    private static final int VIEW_HEIGHT = 320;
    private static final String EMPTY_HINT = "Search a player to see them here";

    private static final double ROTATE_SPEED = 0.01; // radians per pixel dragged
    private static final double MAX_PITCH = 1.4;
    private static final double MIN_ZOOM = 0.3;
    private static final double MAX_ZOOM = 5.0;
    private static final double ZOOM_STEP = 1.1;

    private final ModelRenderer renderer = new ModelRenderer();

    private ModelSnapshot model;
    private double yaw;
    private double pitch;
    private double zoom = 1.0;

    private BufferedImage frame;
    private boolean dirty = true;

    public ModelViewer()
    {
        setOpaque(true);
        setBackground(ColorScheme.DARKER_GRAY_COLOR);
        setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, VIEW_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, VIEW_HEIGHT));
        setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

        OrbitControls controls = new OrbitControls();
        addMouseListener(controls);
        addMouseMotionListener(controls);
        addMouseWheelListener(controls); // also stops the sidebar scrolling while over the viewer
    }

    /** Pass null to clear. */
    public void setModel(@Nullable ModelSnapshot model)
    {
        this.model = model;
        resetView();
    }

    private void resetView()
    {
        yaw = 0;
        pitch = 0;
        zoom = 1.0;
        invalidateFrame();
    }

    private void invalidateFrame()
    {
        dirty = true;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        super.paintComponent(g);

        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0)
        {
            return;
        }

        if (model == null)
        {
            g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(EMPTY_HINT, (width - fm.stringWidth(EMPTY_HINT)) / 2, height / 2);
            return;
        }

        if (frame == null || frame.getWidth() != width || frame.getHeight() != height)
        {
            frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            dirty = true;
        }

        // Only re-render when the view changed; plain repaints just blit the cached frame
        if (dirty)
        {
            renderer.render(model, yaw, pitch, zoom, frame);
            dirty = false;
        }

        g.drawImage(frame, 0, 0, null);
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private final class OrbitControls extends MouseAdapter
    {
        private Point lastDrag;

        @Override
        public void mousePressed(MouseEvent e)
        {
            lastDrag = e.getPoint();
        }

        @Override
        public void mouseDragged(MouseEvent e)
        {
            if (lastDrag != null)
            {
                yaw += (e.getX() - lastDrag.x) * ROTATE_SPEED;
                pitch = clamp(pitch + (e.getY() - lastDrag.y) * ROTATE_SPEED, -MAX_PITCH, MAX_PITCH);
                invalidateFrame();
            }
            lastDrag = e.getPoint();
        }

        @Override
        public void mouseReleased(MouseEvent e)
        {
            lastDrag = null;
        }

        @Override
        public void mouseClicked(MouseEvent e)
        {
            if (e.getClickCount() == 2)
            {
                resetView();
            }
        }

        @Override
        public void mouseWheelMoved(MouseWheelEvent e)
        {
            zoom = clamp(zoom * Math.pow(ZOOM_STEP, -e.getPreciseWheelRotation()), MIN_ZOOM, MAX_ZOOM);
            invalidateFrame();
        }
    }
}