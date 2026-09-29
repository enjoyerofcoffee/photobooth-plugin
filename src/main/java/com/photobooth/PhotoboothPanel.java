package com.photobooth;

import com.photobooth.render.ModelViewer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;

/**
 * Sidebar UI. Everything here runs on the Swing EDT, except {@link #showError} and
 * {@link #showPlayer}, which are safe to call from any thread.
 */
class PhotoboothPanel extends PluginPanel
{
    private static final String IDLE_MESSAGE = "Enter a player name and press Enter.";
    private static final String CONTROLS_HINT = "Drag to rotate, scroll to zoom, double-click to reset";

    private final ItemManager itemManager;
    private final Consumer<String> onLookup;
    private final IconTextField searchBar = new IconTextField();
    private final JLabel statusLabel = new JLabel();
    private final ModelViewer viewer = new ModelViewer();
    private final JPanel equipmentList = new JPanel();

    private boolean lookupInProgress;

    PhotoboothPanel(ItemManager itemManager, Consumer<String> onLookup)
    {
        this.itemManager = Objects.requireNonNull(itemManager, "itemManager");
        this.onLookup = Objects.requireNonNull(onLookup, "onLookup");

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        searchBar.setIcon(IconTextField.Icon.SEARCH);
        searchBar.setPreferredSize(new Dimension(PANEL_WIDTH - 20, 30));
        searchBar.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        searchBar.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
        searchBar.addActionListener(e -> search()); // fires on Enter
        searchBar.addClearListener(this::reset);

        statusLabel.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 2));

        JLabel controlsHint = new JLabel(CONTROLS_HINT);
        controlsHint.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
        controlsHint.setFont(FontManager.getRunescapeSmallFont());
        controlsHint.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));

        equipmentList.setLayout(new BoxLayout(equipmentList, BoxLayout.Y_AXIS));
        equipmentList.setOpaque(false);
        equipmentList.setBorder(BorderFactory.createEmptyBorder(8, 2, 0, 2));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        for (JComponent component : new JComponent[]{searchBar, statusLabel, viewer, controlsHint, equipmentList})
        {
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            content.add(component);
        }

        add(content, BorderLayout.NORTH);
        showStatus(IDLE_MESSAGE, false);
    }

    /** Thread-safe. Ends the current lookup with an error. */
    void showError(String message)
    {
        SwingUtilities.invokeLater(() -> displayError(message));
    }

    /** Thread-safe. Ends the current lookup by showing the player. */
    void showPlayer(PlayerSnapshot snapshot)
    {
        SwingUtilities.invokeLater(() ->
        {
            setLoading(false);
            showStatus("Showing " + snapshot.getPlayerName(), false);
            viewer.setModel(snapshot.getModel());

            equipmentList.removeAll();
            for (EquipmentEntry entry : snapshot.getEquipment())
            {
                equipmentList.add(new EquipmentRow(entry, itemManager.getImage(entry.getItemId())));
            }

            revalidate();
            repaint();
        });
    }

    private void search()
    {
        // JTextField still fires ActionEvents on Enter while non-editable, so guard explicitly
        if (lookupInProgress)
        {
            return;
        }

        String name = PlayerNames.normalize(searchBar.getText());
        if (!PlayerNames.isValid(name))
        {
            displayError("Invalid name. Use 1-12 letters, digits, spaces, _ or -.");
            return;
        }

        setLoading(true);
        showStatus("Looking for " + name + "...", false);
        onLookup.accept(name);
    }

    private void reset()
    {
        // Don't touch the loading state: a lookup may still be running and will finish on its own
        if (!lookupInProgress)
        {
            searchBar.setIcon(IconTextField.Icon.SEARCH);
        }
        clearResult();
        showStatus(IDLE_MESSAGE, false);
    }

    private void displayError(String message)
    {
        setLoading(false);
        searchBar.setIcon(IconTextField.Icon.ERROR);
        clearResult();
        showStatus(message, true);
    }

    private void setLoading(boolean loading)
    {
        lookupInProgress = loading;
        searchBar.setEditable(!loading);
        searchBar.setIcon(loading ? IconTextField.Icon.LOADING : IconTextField.Icon.SEARCH);
    }

    private void clearResult()
    {
        viewer.setModel(null);
        equipmentList.removeAll();
        revalidate();
        repaint();
    }

    private void showStatus(String message, boolean error)
    {
        statusLabel.setForeground(error ? ColorScheme.PROGRESS_ERROR_COLOR : ColorScheme.LIGHT_GRAY_COLOR);
        // <html> gives us line wrapping; escaping means no caller has to guarantee its text is markup-free
        statusLabel.setText("<html>" + escapeHtml(message) + "</html>");
    }

    private static String escapeHtml(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}