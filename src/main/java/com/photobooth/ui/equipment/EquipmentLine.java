package com.photobooth.ui.equipment;

import com.photobooth.contract.EquipmentSlot;

/** One worn item, resolved to display data. Immutable; built on the client thread, read on the EDT. */
public final class EquipmentLine
{
	private final EquipmentSlot slot;
	private final int itemId;
	private final String name;
	/** GE price in coins; 0 when untradeable or unknown. */
	private final int price;

	public EquipmentLine(EquipmentSlot slot, int itemId, String name, int price)
	{
		this.slot = slot;
		this.itemId = itemId;
		this.name = name;
		this.price = price;
	}

	public EquipmentSlot getSlot()
	{
		return slot;
	}

	public int getItemId()
	{
		return itemId;
	}

	public String getName()
	{
		return name;
	}

	public int getPrice()
	{
		return price;
	}
}
