package com.photobooth.contract;

import java.util.Objects;

/**
 * What occupies one appearance slot: either a worn item or a base body kit.
 * <p>
 * The ID is the plain cache ID (an item ID for ITEM, a kit ID for KIT), never the
 * client's internal offset-encoded value. The capture module strips the offsets.
 */
public final class SlotEntry
{
	public enum Type
	{
		ITEM,
		KIT
	}

	private Type type;
	private int id;

	/** No-arg constructor for Gson. */
	SlotEntry()
	{
	}

	public SlotEntry(Type type, int id)
	{
		this.type = Objects.requireNonNull(type, "type");
		this.id = id;
	}

	public static SlotEntry item(int itemId)
	{
		return new SlotEntry(Type.ITEM, itemId);
	}

	public static SlotEntry kit(int kitId)
	{
		return new SlotEntry(Type.KIT, kitId);
	}

	public Type getType()
	{
		return type;
	}

	public int getId()
	{
		return id;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof SlotEntry))
		{
			return false;
		}
		SlotEntry that = (SlotEntry) o;
		return id == that.id && type == that.type;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(type, id);
	}

	@Override
	public String toString()
	{
		return type + ":" + id;
	}
}
