package com.photobooth;

import lombok.Value;
import net.runelite.api.kit.KitType;

@Value
public class EquipmentEntry
{
    KitType slot;
    int itemId;
    String itemName;
}