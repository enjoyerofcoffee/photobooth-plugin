package com.photobooth.ui.equipment;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/**
 * One row: item icon on the left, then name / slot / price stacked on the right.
 * EDT only.
 */
class EquipmentRow extends JPanel
{
	/** Item sprites are 36x32 (Constants.ITEM_SPRITE_WIDTH/HEIGHT). */
	private static final Dimension ICON_SIZE = new Dimension(36, 32);

	EquipmentRow(EquipmentLine line, ItemManager itemManager)
	{
		super(new BorderLayout(8, 0));
		setOpaque(false);
		setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));

		JLabel icon = new JLabel();
		icon.setPreferredSize(ICON_SIZE);
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		// Safe from the EDT: the sprite is drawn on the client thread and the label updates itself.
		AsyncBufferedImage image = itemManager.getImage(line.getItemId());
		if (image != null)
		{
			image.addTo(icon);
		}

		JLabel name = new JLabel(line.getName());
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(java.awt.Color.WHITE);

		JLabel slot = new JLabel(slotLabel(line));
		slot.setFont(FontManager.getRunescapeSmallFont());
		slot.setForeground(ColorScheme.LIGHT_GRAY_COLOR.darker());

		JLabel price = new JLabel(PriceFormat.text(line.getPrice()));
		price.setFont(FontManager.getRunescapeSmallFont());
		price.setForeground(line.getPrice() > 0 ? PriceFormat.color(line.getPrice()) : ColorScheme.LIGHT_GRAY_COLOR);

		JPanel text = new JPanel(new GridLayout(3, 1, 0, 0));
		text.setOpaque(false);
		text.add(name);
		text.add(slot);
		text.add(price);

		add(icon, BorderLayout.WEST);
		add(text, BorderLayout.CENTER);

		// Long names ("Amulet of defence (t)") get truncated with "..."; the tooltip shows it all.
		setToolTipText(line.getName() + " (" + slotLabel(line) + ")");
	}

	/** HEAD -> "Head", like the screenshot. */
	private static String slotLabel(EquipmentLine line)
	{
		String s = line.getSlot().name().toLowerCase();
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
