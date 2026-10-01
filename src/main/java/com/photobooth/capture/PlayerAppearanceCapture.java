package com.photobooth.capture;

import com.photobooth.contract.Appearance;
import com.photobooth.contract.EquipmentSlot;
import com.photobooth.contract.SlotEntry;
import com.photobooth.contract.Usernames;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.kit.KitType;

/**
 * Reads the appearance of a player who is currently loaded in the local scene and converts
 * it into the shared {@link Appearance} contract. This is the ONLY class that touches
 * PlayerComposition, so the rest of the plugin never sees the client's offset encoding.
 * <p>
 * CLIENT THREAD ONLY: every method here reads live game state. Call it through
 * {@code ClientThread.invoke(...)}, never from Swing or an OkHttp callback.
 */
@Slf4j
@Singleton
public class PlayerAppearanceCapture
{
	private final Client client;

	@Inject
	PlayerAppearanceCapture(Client client)
	{
		this.client = client;
	}

	/**
	 * @return the player's appearance if they are loaded near us right now, else empty.
	 */
	public Optional<Appearance> captureNearby(String normalizedName)
	{
		assert client.isClientThread() : "PlayerAppearanceCapture must run on the client thread";

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return Optional.empty();
		}

		for (Player player : worldView.players())
		{
			if (player == null || player.getName() == null)
			{
				continue;
			}
			if (Usernames.normalize(player.getName()).equals(normalizedName))
			{
				return Optional.ofNullable(toAppearance(player));
			}
		}
		return Optional.empty();
	}

	private Appearance toAppearance(Player player)
	{
		PlayerComposition comp = player.getPlayerComposition();
		if (comp == null)
		{
			log.debug("Player {} has no composition yet", player.getName());
			return null;
		}

		int[] raw = comp.getEquipmentIds();
		Map<EquipmentSlot, SlotEntry> equipment = new EnumMap<>(EquipmentSlot.class);
		for (KitType kit : KitType.values())
		{
			if (raw == null || kit.getIndex() >= raw.length)
			{
				break;
			}
			SlotEntry entry = decode(raw[kit.getIndex()]);
			if (entry != null)
			{
				equipment.put(EquipmentSlot.valueOf(kit.name()), entry);
			}
		}

		int transformed = comp.getTransformedNpcId();
		Integer npcTransformId = transformed >= 0 ? transformed : null;

		// Weapon-specific standing pose, sent to the client by the server with the appearance.
		int idle = player.getIdlePoseAnimation();
		Integer idlePoseAnimation = idle >= 0 ? idle : null;

		return new Appearance(
			player.getName().replace(' ', ' '),
			comp.getGender(),
			equipment,
			comp.getColors(),
			npcTransformId,
			idlePoseAnimation,
			Instant.now());
	}

	/**
	 * Client encoding (verified against PlayerComposition source):
	 * value >= ITEM_OFFSET (2048) is an item, value - 2048;
	 * KIT_OFFSET (256) <= value < 2048 is a kit, value - 256;
	 * anything lower means the slot is empty.
	 */
	static SlotEntry decode(int value)
	{
		if (value >= PlayerComposition.ITEM_OFFSET)
		{
			return SlotEntry.item(value - PlayerComposition.ITEM_OFFSET);
		}
		if (value >= PlayerComposition.KIT_OFFSET)
		{
			return SlotEntry.kit(value - PlayerComposition.KIT_OFFSET);
		}
		return null;
	}
}
