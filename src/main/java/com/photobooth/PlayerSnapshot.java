package com.photobooth;

import java.util.List;
import lombok.Value;

@Value
public class PlayerSnapshot
{
    String playerName;
    List<EquipmentEntry> equipment;
}