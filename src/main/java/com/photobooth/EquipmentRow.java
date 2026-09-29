package com.photobooth;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.api.kit.KitType;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.util.AsyncBufferedImage;

/** One equipment row: item icon, then the item name above its slot. EDT only. */
class EquipmentRow extends JPanel
{
    // Inventory item sprites are 36x32
    private static final Dimension ICON_SIZE = new Dimension(36, 32);
    private static final int ICON_TEXT_GAP = 8;

    EquipmentRow(EquipmentEntry entry, AsyncBufferedImage icon)
    {
        setLayout(new BorderLayout(ICON_TEXT_GAP, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel iconLabel = new JLabel();
        iconLabel.setPreferredSize(ICON_SIZE);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        icon.addTo(iconLabel); // shows a blank sprite until loaded, then updates itself

        // Plain text (no leading <html>), so item names from the cache are never parsed as markup.
        // JLabel truncates with "..." when too long; the tooltip shows the full name.
        JLabel nameLabel = new JLabel(entry.getItemName());
        nameLabel.setFont(FontManager.getRunescapeFont());
        nameLabel.setForeground(Color.WHITE);
        nameLabel.setToolTipText(entry.getItemName());

        JLabel slotLabel = new JLabel(slotName(entry.getSlot()));
        slotLabel.setFont(FontManager.getRunescapeSmallFont());
        slotLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

        JPanel text = new JPanel(new GridLayout(2, 1));
        text.setOpaque(false);
        text.add(nameLabel);
        text.add(slotLabel);

        add(iconLabel, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);

        // Stop BoxLayout from stretching rows vertically
        setMaximumSize(new Dimension(Integer.MAX_VALUE, getPreferredSize().height));
    }

    private static String slotName(KitType slot)
    {
        // Locale.ROOT: default-locale lowercasing breaks on e.g. Turkish systems ("SHIELD" -> "shıeld")
        String name = slot.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}