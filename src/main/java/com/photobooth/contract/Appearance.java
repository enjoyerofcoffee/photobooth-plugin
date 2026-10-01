package com.photobooth.contract;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * THE shared contract: everything needed to rebuild a player's 3D model.
 * <p>
 * This is the JSON body the API stores and returns, and the only input the model builder
 * accepts. It contains cache IDs and colour indexes only, never geometry, so the client
 * always rebuilds the model from its own game cache.
 * <p>
 * Pure Java on purpose (no RuneLite/Lombok imports) so it can be copied to the server.
 */
public final class Appearance
{
	public static final int CURRENT_SCHEMA_VERSION = 1;
	public static final int COLOR_COUNT = 5;
	public static final int GENDER_MALE = 0;
	public static final int GENDER_FEMALE = 1;

	private int schemaVersion = CURRENT_SCHEMA_VERSION;

	/** Display name as it appeared in game, e.g. "Iron Man 1". */
	private String displayName;

	/** 0 = male, 1 = female (same as PlayerComposition#getGender). */
	private int gender;

	/** Occupied slots only. A missing slot means "nothing rendered there". */
	private Map<EquipmentSlot, SlotEntry> equipment = new EnumMap<>(EquipmentSlot.class);

	/**
	 * Five body colour indexes (hair, torso, legs, feet, skin), exactly as returned by
	 * PlayerComposition#getColors. They are palette indexes, not RGB.
	 */
	private int[] bodyColors = new int[COLOR_COUNT];

	/** NPC ID the player is transformed into (e.g. a transformation ring), or null. */
	private Integer npcTransformId;

	/**
	 * The player's own standing animation ID (it depends on their weapon), or null if unknown.
	 * The model builder poses the character with this; when null it falls back to the
	 * default unarmed stand. Optional and additive, so schemaVersion stays 1.
	 */
	private Integer idlePoseAnimation;

	/** ISO-8601 UTC instant when this appearance was captured, e.g. "2026-09-30T09:24:00Z". */
	private String capturedAt;

	/** No-arg constructor for Gson. */
	public Appearance()
	{
	}

	public Appearance(String displayName, int gender, Map<EquipmentSlot, SlotEntry> equipment,
		int[] bodyColors, Integer npcTransformId, Integer idlePoseAnimation, Instant capturedAt)
	{
		this.idlePoseAnimation = idlePoseAnimation;
		this.displayName = displayName;
		this.gender = gender;
		this.equipment = equipment.isEmpty()
			? new EnumMap<>(EquipmentSlot.class)
			: new EnumMap<>(equipment);
		this.bodyColors = Arrays.copyOf(bodyColors, bodyColors.length);
		this.npcTransformId = npcTransformId;
		this.capturedAt = capturedAt.toString();
	}

	public int getSchemaVersion()
	{
		return schemaVersion;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** Derived, never serialised separately: the API keys rows on this. */
	public String getNormalizedName()
	{
		return Usernames.normalize(displayName);
	}

	public int getGender()
	{
		return gender;
	}

	public Map<EquipmentSlot, SlotEntry> getEquipment()
	{
		return equipment == null ? Collections.emptyMap() : Collections.unmodifiableMap(equipment);
	}

	public int[] getBodyColors()
	{
		return bodyColors == null ? new int[COLOR_COUNT] : Arrays.copyOf(bodyColors, bodyColors.length);
	}

	public Integer getNpcTransformId()
	{
		return npcTransformId;
	}

	public Integer getIdlePoseAnimation()
	{
		return idlePoseAnimation;
	}

	public String getCapturedAt()
	{
		return capturedAt;
	}

	/**
	 * Structural validation, run on everything received from the API before it reaches the
	 * model builder. The server should run the same checks on PUT.
	 *
	 * @return human-readable problems; empty means valid.
	 */
	public List<String> validate()
	{
		List<String> problems = new ArrayList<>();
		if (schemaVersion != CURRENT_SCHEMA_VERSION)
		{
			problems.add("unsupported schemaVersion " + schemaVersion);
		}
		if (!Usernames.isPlausible(Usernames.normalize(displayName)))
		{
			problems.add("invalid displayName");
		}
		if (gender != GENDER_MALE && gender != GENDER_FEMALE)
		{
			problems.add("gender must be 0 or 1");
		}
		if (bodyColors == null || bodyColors.length != COLOR_COUNT)
		{
			problems.add("bodyColors must have exactly " + COLOR_COUNT + " entries");
		}
		else
		{
			for (int c : bodyColors)
			{
				if (c < 0)
				{
					problems.add("bodyColors must be non-negative");
					break;
				}
			}
		}
		if (equipment != null)
		{
			for (Map.Entry<EquipmentSlot, SlotEntry> e : equipment.entrySet())
			{
				if (e.getKey() == null || e.getValue() == null || e.getValue().getType() == null)
				{
					problems.add("equipment contains a null slot/entry");
				}
				else if (e.getValue().getId() < 0)
				{
					problems.add("negative id in slot " + e.getKey());
				}
			}
		}
		if (npcTransformId != null && npcTransformId < 0)
		{
			problems.add("npcTransformId must be null or non-negative");
		}
		if (idlePoseAnimation != null && idlePoseAnimation < 0)
		{
			problems.add("idlePoseAnimation must be null or non-negative");
		}
		try
		{
			if (capturedAt == null)
			{
				problems.add("capturedAt missing");
			}
			else
			{
				Instant.parse(capturedAt);
			}
		}
		catch (DateTimeParseException ex)
		{
			problems.add("capturedAt is not ISO-8601");
		}
		return problems;
	}

	@Override
	public String toString()
	{
		return "Appearance{" + displayName + ", gender=" + gender + ", equipment=" + equipment
			+ ", colors=" + Arrays.toString(bodyColors) + ", npc=" + npcTransformId
			+ ", idlePose=" + idlePoseAnimation
			+ ", capturedAt=" + capturedAt + "}";
	}
}
