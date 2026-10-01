package com.photobooth.contract;

/**
 * The 12 appearance slots of an OSRS player, named after RuneLite's {@code KitType}.
 * <p>
 * This is deliberately a copy rather than {@code KitType} itself, so the contract package has
 * zero RuneLite dependencies and the exact same schema can be implemented by the API server.
 * The capture module maps {@code KitType} -> {@code EquipmentSlot} by name.
 * <p>
 * Body-part slots (TORSO, ARMS, LEGS, HANDS, BOOTS, HAIR, JAW) hold a KIT when no item
 * covers them. JAW is the beard/jaw slot.
 */
public enum EquipmentSlot
{
	HEAD,
	CAPE,
	AMULET,
	WEAPON,
	TORSO,
	SHIELD,
	ARMS,
	LEGS,
	HAIR,
	HANDS,
	BOOTS,
	JAW
}
