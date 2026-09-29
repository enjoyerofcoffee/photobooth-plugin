package com.photobooth;

import com.photobooth.render.ModelSnapshot;
import java.util.List;
import lombok.Value;

@Value
public class PlayerSnapshot
{
    String playerName;
    List<EquipmentEntry> equipment; // callers pass an unmodifiable list (List.copyOf)
    ModelSnapshot model;
}