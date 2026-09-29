package com.photobooth;

import com.photobooth.render.ModelSnapshot;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
		name = "Photobooth",
		description = "Preview nearby players and their equipment in a 3D photobooth",
		tags = {"player", "model", "equipment", "outfit", "cosmetic", "screenshot"}
)
public class PhotoboothPlugin extends Plugin
{
	private static final int NAV_PRIORITY = 7;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ItemManager itemManager;

	private PhotoboothPanel panel;
	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		// Created here (not injected) so each enable gets a fresh panel with its callback fixed at construction
		panel = new PhotoboothPanel(itemManager, this::lookup);

		navButton = NavigationButton.builder()
				.tooltip("Photobooth")
				.priority(NAV_PRIORITY)
				.icon(createPlaceholderIcon())
				.panel(panel)
				.build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
	}

	/** Called on the Swing EDT by the panel. Hops to the client thread to read game state. */
	private void lookup(String name)
	{
		// Capture now: if the plugin shuts down mid-lookup, we update a detached panel instead of throwing an NPE
		PhotoboothPanel view = panel;

		clientThread.invoke(() ->
		{
			if (client.getGameState() != GameState.LOGGED_IN)
			{
				view.showError("You need to be logged in to look up players.");
				return;
			}

			Player player = findPlayer(name);
			if (player == null)
			{
				view.showError(name + " isn't loaded nearby. Players must be in your scene to be photographed.");
				return;
			}

			ModelSnapshot model = ModelSnapshot.from(player.getModel());
			if (model == null)
			{
				view.showError("Couldn't read " + name + "'s model. Try again in a moment.");
				return;
			}

			PlayerSnapshot snapshot = new PlayerSnapshot(
					PlayerNames.normalize(player.getName()),
					readEquipment(player),
					model);

			log.debug("Snapshot of {}: {} vertices, {} faces, {} equipment slots",
					snapshot.getPlayerName(), model.getVertexCount(), model.getFaceCount(), snapshot.getEquipment().size());

			view.showPlayer(snapshot);
		});
	}

	/** Client thread only. {@code name} must already be normalized. */
	@Nullable
	private Player findPlayer(String name)
	{
		for (Player player : client.getTopLevelWorldView().players())
		{
			String playerName = player == null ? null : player.getName();
			if (playerName != null && PlayerNames.normalize(playerName).equalsIgnoreCase(name))
			{
				return player;
			}
		}
		return null;
	}

	/** Client thread only (ItemManager.getItemComposition reads the game cache). */
	private List<EquipmentEntry> readEquipment(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return List.of();
		}

		List<EquipmentEntry> entries = new ArrayList<>();
		for (KitType slot : KitType.values())
		{
			int itemId = composition.getEquipmentId(slot);
			if (itemId != -1) // -1 = nothing equipped in this slot
			{
				entries.add(new EquipmentEntry(slot, itemId, itemManager.getItemComposition(itemId).getName()));
			}
		}
		return List.copyOf(entries);
	}

	/** Generated so no resource file is needed yet. Swap for a 16x16 PNG via ImageUtil.loadImageResource later. */
	private static BufferedImage createPlaceholderIcon()
	{
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setColor(new Color(200, 200, 200));
		g.fillRoundRect(1, 4, 14, 10, 3, 3);
		g.fillRect(5, 2, 6, 3);
		g.setColor(new Color(60, 60, 60));
		g.fillOval(5, 6, 6, 6);
		g.dispose();
		return img;
	}
}