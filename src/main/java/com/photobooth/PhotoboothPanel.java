package com.photobooth;

import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Singleton
public class PhotoboothPanel extends PluginPanel
{
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z0-9 _-]{1,12}$");

    private final IconTextField searchBar = new IconTextField();

    private final JLabel statusLabel = new JLabel();
    private final JPanel equipmentPanel = new JPanel();

    private Consumer<String> lookupHandler;

    @Inject
    PhotoboothPanel()
    {
        super();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PluginPanel.PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.addActionListener(e -> search()); // fires on Enter
        searchBar.addClearListener(this::reset);

        // Will probably remove this later
        statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(searchBar);
        top.add(statusLabel);

        equipmentPanel.setLayout(new BoxLayout(equipmentPanel, BoxLayout.Y_AXIS));
        equipmentPanel.setOpaque(false);
        equipmentPanel.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));
        top.add(equipmentPanel);

        add(top, BorderLayout.NORTH);
        showStatus("Enter a player name and press Enter.", false);
    }

    void setLookupHandler(Consumer<String> handler)
    {
        this.lookupHandler = handler;
    }

    // Temporary
    private static String slotLabel(EquipmentEntry entry)
    {
        String name = entry.getSlot().name().toLowerCase();
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    // Will likely make this into the 3d renderer
    void showEquipment(PlayerSnapshot snapshot)
    {
        SwingUtilities.invokeLater(() ->
        {
            searchBar.setEditable(true);
            searchBar.setIcon(IconTextField.Icon.SEARCH);
            showStatus("Showing " + snapshot.getPlayerName() + " (" + snapshot.getEquipment().size() + " items)", false);

            equipmentPanel.removeAll();
            for (EquipmentEntry entry : snapshot.getEquipment())
            {
                // Plain-text labels (no <html>) because item names come from the game cache.
                JLabel row = new JLabel(slotLabel(entry) + ": " + entry.getItemName());
                row.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
                row.setFont(FontManager.getRunescapeSmallFont());
                row.setAlignmentX(Component.LEFT_ALIGNMENT);
                equipmentPanel.add(row);
            }

            revalidate();
            repaint();
        });
    }


    private void search()
    {
        String name = searchBar.getText().trim().replace('\u00A0', ' ');

        if (!NAME_PATTERN.matcher(name).matches())
        {
            showResult("Invalid name. Use 1-12 letters, digits, spaces, _ or -.", true);
            return;
        }

        if (lookupHandler == null)
        {
            return;
        }

        searchBar.setIcon(IconTextField.Icon.LOADING);
        searchBar.setEditable(false);
        showStatus("Looking for " + name + "...", false);

        lookupHandler.accept(name);
    }

    private void reset()
    {
        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setEditable(true);
        showStatus("Enter a player name and press Enter.", false);
    }

    // This just shows the message below the search bar.
    // In the future, I want the error message to be in the search bar itself
    void showResult(String message, boolean error)
    {
        SwingUtilities.invokeLater(() ->
        {
            searchBar.setEditable(true);
            searchBar.setIcon(error ? IconTextField.Icon.ERROR : IconTextField.Icon.SEARCH);
            showStatus(message, error);
        });
    }

    // Names are regex-validated (no HTML chars), so wrapping in <html> for line wrapping is safe.
    private void showStatus(String message, boolean error)
    {
        statusLabel.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
        statusLabel.setText("<html>" + message + "</html>");
    }
}
