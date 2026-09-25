package com.photobooth;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.kit.KitType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@PluginDescriptor(
	name = "Photobooth"
)
public class PhotoboothPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PhotoboothConfig config;

	@Inject
	private PhotoboothPanel panel;

    @Inject
	private ItemManager itemManager;

	@Override
	protected void startUp()
	{
		panel.setLookupHandler(this::lookup);

        NavigationButton navButton = NavigationButton.builder()
                .tooltip("Photobooth")
                .priority(7)
                .icon(createPlaceholderIcon())
                .panel(panel)
                .build();

		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Photobooth stopped!");
	}

	private void lookup(String name)
	{
		clientThread.invoke(() ->
		{
			Player found = null;

			// Hit Photobooth API
			// Dummy API Call for now
			boolean playerFound = false;
			if(playerFound)
			{
				// TODO: API call done here
			}
			else
			{
				// Search for players in the logged in world
				for (Player player : client.getPlayers())
				{
					if (player == null ||
							player.getName() == null)
					{
						continue;
					}
					// In-game names can contain non-breaking spaces
					String playerName = player.getName().replace('\u00A0', ' ');
					if (playerName.equalsIgnoreCase(name))
					{
						found = player;
						break;
					}
				}
			}

			if (found == null)
			{
				panel.showResult(name + " isn't loaded nearby. Players must be in your scene in order to retrieve equipment information", true);
				return;
			}

			PlayerSnapshot snapshot = readEquipment(found);
			panel.showEquipment(snapshot);

			panel.showResult("Found " + found.getName(), false);
		});
	}

	private PlayerSnapshot readEquipment(Player player)
	{
		PlayerComposition composition = player.getPlayerComposition();
		if (composition == null)
		{
			return null;
		}

		List<EquipmentEntry> entries = new ArrayList<EquipmentEntry>();
		for (KitType slot : KitType.values())
		{
			int itemId = composition.getEquipmentId(slot);
			if (itemId == -1)
			{
				continue; // nothing equipped in this slot
			}

			String itemName = itemManager.getItemComposition(itemId).getName();
			entries.add(new EquipmentEntry(slot, itemId, itemName));
		}

		return new PlayerSnapshot(
				player.getName().replace('\u00A0', ' '),
				Collections.unmodifiableList(entries));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{

	}

	/** Simple generated icon so you don't need a resource file yet. Swap for a 16x16 PNG later. */
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

	@Provides
	PhotoboothConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PhotoboothConfig.class);
	}
}
