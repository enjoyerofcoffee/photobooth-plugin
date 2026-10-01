package com.photobooth.ui.equipment;

import com.photobooth.contract.Appearance;
import com.photobooth.render.AppearanceView;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Equipment list shown under the 3D model: icon, name, slot and GE price per item, plus a total.
 * <p>
 * Like the model viewer, it receives only an {@link Appearance}. Names and prices are resolved
 * on the client thread, then the rows are built on the EDT.
 */
@Slf4j
public class EquipmentSummaryPanel extends JPanel implements AppearanceView
{
	private final EquipmentSummaryResolver resolver;
	private final ItemManager itemManager;
	private final ClientThread clientThread;
	private final AtomicInteger requestId = new AtomicInteger();

	private final JPanel rows = new JPanel();
	private final JLabel totalValue = new JLabel();
	private final JPanel totalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

	public EquipmentSummaryPanel(EquipmentSummaryResolver resolver, ItemManager itemManager, ClientThread clientThread)
	{
		super(new BorderLayout(0, 6));
		this.resolver = resolver;
		this.itemManager = itemManager;
		this.clientThread = clientThread;

		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));

		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setOpaque(false);

		JLabel totalLabel = new JLabel("Total: ");
		totalLabel.setFont(FontManager.getRunescapeFont());
		totalLabel.setForeground(Color.WHITE);
		totalValue.setFont(FontManager.getRunescapeFont());
		totalRow.setOpaque(false);
		totalRow.add(totalLabel);
		totalRow.add(totalValue);

		add(rows, BorderLayout.CENTER);
		add(totalRow, BorderLayout.SOUTH);
		clear();
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
		int id = requestId.incrementAndGet();
		showMessage("Loading equipment...");
		setVisible(true);

		clientThread.invoke(() ->
		{
			EquipmentSummary summary;
			try
			{
				summary = resolver.resolve(appearance);
			}
			catch (RuntimeException e)
			{
				log.warn("Photobooth couldn't resolve equipment", e);
				SwingUtilities.invokeLater(() ->
				{
					if (id == requestId.get())
					{
						showMessage("Couldn't load equipment.");
					}
				});
				return;
			}
			SwingUtilities.invokeLater(() ->
			{
				if (id == requestId.get())
				{
					showSummary(summary);
				}
			});
		});
	}

	@Override
	public void clear()
	{
		requestId.incrementAndGet();
		rows.removeAll();
		totalRow.setVisible(false);
		setVisible(false);
		revalidate();
		repaint();
	}

	// ---- internals (EDT) ----

	private void showSummary(EquipmentSummary summary)
	{
		rows.removeAll();
		if (summary.isEmpty())
		{
			showMessage("No equipment worn.");
			return;
		}
		for (EquipmentLine line : summary.getLines())
		{
			EquipmentRow row = new EquipmentRow(line, itemManager);
			row.setAlignmentX(LEFT_ALIGNMENT);
			rows.add(row);
		}
		long total = summary.getTotalPrice();
		totalValue.setText(PriceFormat.text(total));
		totalValue.setForeground(total > 0 ? PriceFormat.color(total) : ColorScheme.LIGHT_GRAY_COLOR);
		totalRow.setVisible(true);
		revalidate();
		repaint();
	}

	private void showMessage(String message)
	{
		rows.removeAll();
		JLabel label = new JLabel(message);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(LEFT_ALIGNMENT);
		rows.add(label);
		totalRow.setVisible(false);
		revalidate();
		repaint();
	}
}
