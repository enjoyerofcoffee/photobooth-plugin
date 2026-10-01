package com.photobooth.render;

import com.photobooth.contract.Appearance;
import com.photobooth.model.AppearanceModelBuilder;
import com.photobooth.model.MeshSnapshot;
import com.photobooth.model.ModelBuildResult;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * The 3D view. Receives only an {@link Appearance}; builds the mesh on the client thread,
 * then renders and handles interaction on the Swing EDT.
 * <p>
 * Drag to rotate, scroll to zoom, double-click to reset the camera.
 * Renders only when something changes (model, size, camera), never per game frame.
 */
@Slf4j
public class ModelViewer extends JPanel implements AppearanceView
{
	private static final double DEFAULT_YAW = Math.toRadians(-30);
	private static final double DEFAULT_PITCH = Math.toRadians(8);
	private static final double MIN_PITCH = Math.toRadians(-60);
	private static final double MAX_PITCH = Math.toRadians(60);
	private static final Color BACKGROUND = ColorScheme.DARKER_GRAY_COLOR;

	private final AppearanceModelBuilder builder;
	private final ClientThread clientThread;
	private final AtomicInteger buildId = new AtomicInteger();

	// EDT-only state
	private MeshSnapshot mesh;
	private String message = "Search for a player to see them here.";
	private double yaw = DEFAULT_YAW;
	private double pitch = DEFAULT_PITCH;
	private double zoom = 1.0;
	private BufferedImage frame;
	private boolean dirty = true;
	private Point dragStart;

	public ModelViewer(AppearanceModelBuilder builder, ClientThread clientThread)
	{
		this.builder = builder;
		this.clientThread = clientThread;

		setBackground(BACKGROUND);
		setPreferredSize(new Dimension(0, 380));
		setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

		MouseAdapter mouse = new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				dragStart = e.getPoint();
			}

			@Override
			public void mouseDragged(MouseEvent e)
			{
				if (dragStart == null || mesh == null)
				{
					return;
				}
				yaw += (e.getX() - dragStart.x) * 0.012;
				pitch = clamp(pitch + (e.getY() - dragStart.y) * 0.008, MIN_PITCH, MAX_PITCH);
				dragStart = e.getPoint();
				invalidateFrame();
			}

			@Override
			public void mouseReleased(MouseEvent e)
			{
				dragStart = null;
			}

			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() == 2)
				{
					resetCamera();
				}
			}
		};
		addMouseListener(mouse);
		addMouseMotionListener(mouse);
	}

	// ---- AppearanceView (EDT) ----

	@Override
	public JComponent getComponent()
	{
		return this;
	}

	@Override
	public void show(Appearance appearance)
	{
		int id = buildId.incrementAndGet();
		mesh = null;
		message = "Building model...";
		invalidateFrame();

		// Model building reads/writes game state: client thread only.
		clientThread.invoke(() ->
		{
			ModelBuildResult result = builder.build(appearance);
			SwingUtilities.invokeLater(() -> onBuilt(id, result));
		});
	}

	@Override
	public void clear()
	{
		buildId.incrementAndGet();
		mesh = null;
		message = "";
		invalidateFrame();
	}

	/** Current view as an image of the given size; used by screenshots later. */
	public BufferedImage renderImage(int width, int height, int backgroundArgb)
	{
		return SoftwareRasterizer.render(mesh, width, height,
			new SoftwareRasterizer.Camera(yaw, pitch, zoom), backgroundArgb);
	}

	public boolean hasModel()
	{
		return mesh != null;
	}

	// ---- internals (EDT) ----

	private void onBuilt(int id, ModelBuildResult result)
	{
		if (id != buildId.get())
		{
			return; // superseded by a newer search
		}
		if (result.isSuccess())
		{
			mesh = result.getMesh();
			message = null;
			log.debug("Photobooth model built: {} vertices, {} faces", mesh.getVertexCount(), mesh.getFaceCount());
		}
		else
		{
			message = result.getError();
		}
		resetCamera();
	}

	private void resetCamera()
	{
		yaw = DEFAULT_YAW;
		pitch = DEFAULT_PITCH;
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
		int w = getWidth();
		int h = getHeight();
		if (w <= 0 || h <= 0)
		{
			return;
		}

		if (mesh != null)
		{
			if (dirty || frame == null || frame.getWidth() != w || frame.getHeight() != h)
			{
				long start = System.nanoTime();
				frame = renderImage(w, h, BACKGROUND.getRGB());
				dirty = false;
				log.trace("Photobooth frame {}x{} in {} ms", w, h, (System.nanoTime() - start) / 1_000_000);
			}
			g.drawImage(frame, 0, 0, null);
		}

		if (message != null && !message.isEmpty())
		{
			drawMessage((Graphics2D) g, w, h);
		}
	}

	private void drawMessage(Graphics2D g, int w, int h)
	{
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setFont(FontManager.getRunescapeSmallFont());
		g.setColor(ColorScheme.LIGHT_GRAY_COLOR);
		FontMetrics fm = g.getFontMetrics();

		// Simple word wrap so long errors fit the 225px sidebar.
		int maxWidth = w - 20;
		StringBuilder line = new StringBuilder();
		int y = h / 2 - fm.getHeight();
		for (String word : message.split(" "))
		{
			String candidate = line.length() == 0 ? word : line + " " + word;
			if (fm.stringWidth(candidate) > maxWidth && line.length() > 0)
			{
				g.drawString(line.toString(), (w - fm.stringWidth(line.toString())) / 2, y);
				y += fm.getHeight();
				line = new StringBuilder(word);
			}
			else
			{
				line = new StringBuilder(candidate);
			}
		}
		g.drawString(line.toString(), (w - fm.stringWidth(line.toString())) / 2, y);
	}

	private static double clamp(double v, double min, double max)
	{
		return Math.max(min, Math.min(max, v));
	}
}
