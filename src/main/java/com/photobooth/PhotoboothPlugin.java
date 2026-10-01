package com.photobooth;

import com.google.inject.Provides;
import com.photobooth.lookup.AppearanceLookupService;
import com.photobooth.model.AppearanceModelBuilder;
import com.photobooth.render.ModelViewer;
import com.photobooth.ui.PhotoboothPanel;
import com.photobooth.ui.equipment.EquipmentSummaryPanel;
import com.photobooth.ui.equipment.EquipmentSummaryResolver;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "Photobooth",
	description = "Look up a player and view their character and equipment in 3D",
	tags = {"player", "model", "equipment", "inspect", "fashion", "screenshot"}
)
public class PhotoboothPlugin extends Plugin
{
	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private AppearanceLookupService lookupService;

	@Inject
	private AppearanceModelBuilder modelBuilder;

	@Inject
	private ClientThread clientThread;

	@Inject
	private EquipmentSummaryResolver equipmentResolver;

	@Inject
	private ItemManager itemManager;

	private NavigationButton navButton;

	@Override
	protected void startUp()
	{
		ModelViewer viewer = new ModelViewer(modelBuilder, clientThread);
		EquipmentSummaryPanel equipment = new EquipmentSummaryPanel(equipmentResolver, itemManager, clientThread);
		PhotoboothPanel panel = new PhotoboothPanel(lookupService, viewer, equipment);
		BufferedImage icon = ImageUtil.loadImageResource(PhotoboothPlugin.class, "icon.png");

		navButton = NavigationButton.builder()
			.tooltip("Photobooth")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();

		clientToolbar.addNavigation(navButton);
		log.debug("Photobooth started");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		lookupService.clearCache();
		log.debug("Photobooth stopped");
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		// Switching API URL/key must not serve results cached from the old source.
		if (PhotoboothConfig.GROUP.equals(event.getGroup()))
		{
			lookupService.clearCache();
		}
	}

	@Provides
	PhotoboothConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PhotoboothConfig.class);
	}
}
