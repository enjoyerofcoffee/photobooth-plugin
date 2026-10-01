package com.photobooth.ui;

import com.photobooth.lookup.AppearanceLookupService;
import com.photobooth.lookup.LookupResult;
import com.photobooth.render.AppearanceView;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: search bar, status line, and the appearance view.
 * <p>
 * Everything in here runs on the Swing EDT. Lookup futures complete on other threads, so
 * every UI update is wrapped in SwingUtilities.invokeLater.
 */
@Slf4j
public class PhotoboothPanel extends PluginPanel
{
	private final AppearanceLookupService lookupService;
	private final AppearanceView modelView;
	private final AppearanceView equipmentView;

	private final JTextField searchField = new JTextField();
	private final JButton searchButton = new JButton("Look up");
	private final JLabel status = new JLabel("Enter a player name");

	/** Ignores results from older searches if the user searches again quickly. */
	private final AtomicInteger requestId = new AtomicInteger();

	/**
	 * @param modelView     the 3D model, shown under the search bar
	 * @param equipmentView the equipment list, shown under the model
	 */
	public PhotoboothPanel(AppearanceLookupService lookupService, AppearanceView modelView,
		AppearanceView equipmentView)
	{
		// Default PluginPanel wraps us in a scroll pane, so the model keeps its full height and
		// the sidebar scrolls when the equipment list is long. Mouse-wheel over the model still
		// zooms, because the model viewer handles wheel events itself.
		super();
		this.lookupService = lookupService;
		this.modelView = modelView;
		this.equipmentView = equipmentView;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel searchRow = new JPanel(new BorderLayout(6, 0));
		searchRow.setOpaque(false);
		searchField.setPreferredSize(new Dimension(0, 30));
		searchField.addActionListener(e -> search());
		searchButton.addActionListener(e -> search());
		searchRow.add(searchField, BorderLayout.CENTER);
		searchRow.add(searchButton, BorderLayout.EAST);

		JPanel top = new JPanel(new BorderLayout(0, 6));
		top.setOpaque(false);
		top.add(searchRow, BorderLayout.NORTH);
		top.add(status, BorderLayout.SOUTH);

		add(top, BorderLayout.NORTH);
		add(modelView.getComponent(), BorderLayout.CENTER);
		add(equipmentView.getComponent(), BorderLayout.SOUTH);
	}

	private void search()
	{
		String name = searchField.getText();
		int id = requestId.incrementAndGet();

		setBusy(true);
		status.setText("Searching...");
		modelView.clear();
		equipmentView.clear();

		lookupService.lookup(name).whenComplete((result, error) ->
			SwingUtilities.invokeLater(() ->
			{
				if (id != requestId.get())
				{
					return; // a newer search has started
				}
				setBusy(false);
				if (error != null)
				{
					log.warn("Photobooth lookup failed", error);
					status.setText("Something went wrong. See logs.");
					return;
				}
				show(result);
			}));
	}

	private void show(LookupResult result)
	{
		status.setText("<html>" + escape(result.getMessage()) + "</html>");
		if (result.hasAppearance())
		{
			// Each view gets the appearance ONLY, never the lookup result.
			modelView.show(result.getAppearance());
			equipmentView.show(result.getAppearance());
		}
	}

	private void setBusy(boolean busy)
	{
		searchField.setEnabled(!busy);
		searchButton.setEnabled(!busy);
	}

	private static String escape(String s)
	{
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
}
