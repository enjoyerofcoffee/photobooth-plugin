package com.photobooth.render;

import com.photobooth.contract.Appearance;
import com.photobooth.contract.EquipmentSlot;
import com.photobooth.contract.SlotEntry;
import java.awt.BorderLayout;
import java.util.Arrays;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/**
 * Temporary text-only view for milestones (a)-(c): proves the data pipeline end to end
 * before any 3D work. Replaced by the model renderer in milestone (d).
 */
public class AppearanceSummaryView implements AppearanceView
{
	private final JPanel root = new JPanel(new BorderLayout());
	private final JTextArea text = new JTextArea();

	public AppearanceSummaryView()
	{
		text.setEditable(false);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setFont(FontManager.getRunescapeSmallFont());
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		text.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		root.add(text, BorderLayout.CENTER);
	}

	@Override
	public JComponent getComponent()
	{
		return root;
	}

	@Override
	public void show(Appearance a)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(a.getDisplayName()).append('\n');
		sb.append(a.getGender() == Appearance.GENDER_FEMALE ? "Female" : "Male").append('\n');
		if (a.getNpcTransformId() != null)
		{
			sb.append("Transformed into NPC ").append(a.getNpcTransformId()).append('\n');
		}
		sb.append("Colours ").append(Arrays.toString(a.getBodyColors())).append("\n\n");

		Map<EquipmentSlot, SlotEntry> eq = a.getEquipment();
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			SlotEntry e = eq.get(slot);
			sb.append(slot).append(": ").append(e == null ? "-" : e.toString()).append('\n');
		}
		sb.append("\nCaptured ").append(a.getCapturedAt());
		text.setText(sb.toString());
		text.setCaretPosition(0);
	}

	@Override
	public void clear()
	{
		text.setText("");
	}
}
