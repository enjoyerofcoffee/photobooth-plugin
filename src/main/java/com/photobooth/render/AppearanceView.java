package com.photobooth.render;

import com.photobooth.contract.Appearance;
import javax.swing.JComponent;

/**
 * The seam between lookup and display. The panel hands an {@link Appearance} to whatever
 * implements this and never learns how it is drawn.
 * <p>
 * Implemented by {@link ModelViewer}, which builds and draws the 3D model. Nothing else in
 * the plugin needs to change if the rendering approach changes.
 * <p>
 * All methods are called on the Swing EDT.
 */
public interface AppearanceView
{
	JComponent getComponent();

	void show(Appearance appearance);

	void clear();
}
