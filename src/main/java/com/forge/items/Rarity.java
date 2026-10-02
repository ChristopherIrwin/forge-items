package com.forge.items;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/** Item rarity tiers, shown as a colored lore line and used for GUI sorting. */
public enum Rarity {
    COMMON("<gray>"),
    UNCOMMON("<green>"),
    RARE("<aqua>"),
    EPIC("<light_purple>"),
    LEGENDARY("<gold>"),
    MYTHIC("<red>");

    private final String colorTag;

    Rarity(String colorTag) {
        this.colorTag = colorTag;
    }

    /** Parses a rarity name; returns null for unknown input (caller warns). */
    public static @Nullable Rarity fromString(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** MiniMessage line for the item lore, e.g. "<gold>◆ Legendary". */
    public String loreLine() {
        String name = name();
        return colorTag + "◆ " + name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Plain pretty name, e.g. "Legendary". */
    public String pretty() {
        String name = name();
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
