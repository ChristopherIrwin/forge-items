package com.forge.items;

import org.jetbrains.annotations.Nullable;

/** All activator trigger types supported by ForgeItems. */
public enum Trigger {
    RIGHT_CLICK,
    LEFT_CLICK,
    SHIFT_RIGHT_CLICK,
    HIT_ENTITY,
    KILL_ENTITY,
    BLOCK_BREAK,
    BLOCK_PLACE,
    TAKE_DAMAGE,
    EQUIP,
    UNEQUIP,
    CONSUME,
    PROJECTILE_HIT,
    PROJECTILE_LAUNCH,
    SNEAK_TOGGLE,
    SNEAK_START,
    ITEM_DROP,
    ITEM_PICKUP,
    PLAYER_DEATH,
    FISH_CAUGHT,
    LOOP,
    /** Synthetic marker for set-bonus reward execution; never fired by events. */
    SET_BONUS;

    /** Parses a trigger name from config; returns null and logs nothing (caller warns). */
    public static @Nullable Trigger parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Trigger.valueOf(raw.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
