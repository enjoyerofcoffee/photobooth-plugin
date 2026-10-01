package com.photobooth.ui.equipment;

import com.photobooth.contract.Appearance;
import com.photobooth.contract.EquipmentSlot;
import com.photobooth.contract.SlotEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.game.ItemManager;

/**
 * Turns an {@link Appearance} into display data: item names and GE prices.
 * <p>
 * CLIENT THREAD ONLY: ItemManager#getItemComposition and #getItemPrice read item definitions
 * from the game cache.
 * <p>
 * Only ITEM slots are listed; KIT slots are bare body parts (hair, arms...), not equipment.
 * Rings, ammo and other items that aren't drawn on the character are not part of a player's
 * appearance, so they can't be shown for other players.
 */
@Singleton
public class EquipmentSummaryResolver
{
	private final Client client;
	private final ItemManager itemManager;

	@Inject
	EquipmentSummaryResolver(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	public EquipmentSummary resolve(Appearance appearance)
	{
		assert client.isClientThread() : "EquipmentSummaryResolver must run on the client thread";

		List<EquipmentLine> lines = new ArrayList<>();
		Set<Integer> seen = new HashSet<>();

		// EquipmentSlot order matches the in-game equipment tab: head, cape, amulet, weapon...
		for (EquipmentSlot slot : EquipmentSlot.values())
		{
			SlotEntry entry = appearance.getEquipment().get(slot);
			if (entry == null || entry.getType() != SlotEntry.Type.ITEM)
			{
				continue;
			}
			int itemId = entry.getId();
			if (!seen.add(itemId))
			{
				continue; // never count one item twice
			}

			String name = itemManager.getItemComposition(itemId).getName();
			int price = Math.toIntExact(itemManager.getItemPrice(itemId));
			lines.add(new EquipmentLine(slot, itemId, name, Math.max(0, price)));
		}
		return new EquipmentSummary(lines);
	}
}
