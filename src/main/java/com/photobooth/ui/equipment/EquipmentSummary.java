package com.photobooth.ui.equipment;

import java.util.Collections;
import java.util.List;

/** Everything the equipment list displays. Immutable. */
public final class EquipmentSummary
{
	private final List<EquipmentLine> lines;
	private final long totalPrice;

	public EquipmentSummary(List<EquipmentLine> lines)
	{
		this.lines = Collections.unmodifiableList(lines);
		long total = 0;
		for (EquipmentLine line : lines)
		{
			total += line.getPrice();
		}
		this.totalPrice = total;
	}

	public List<EquipmentLine> getLines()
	{
		return lines;
	}

	/** long: a full set of expensive gear can exceed Integer.MAX_VALUE. */
	public long getTotalPrice()
	{
		return totalPrice;
	}

	public boolean isEmpty()
	{
		return lines.isEmpty();
	}
}
