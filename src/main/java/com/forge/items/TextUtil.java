package com.forge.items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.ParsingException;
import org.jetbrains.annotations.Nullable;

/** MiniMessage parsing with a safe plain-text fallback. */
public final class TextUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private TextUtil() {}

    /** Parses MiniMessage; falls back to literal text on malformed input. Never throws, never returns null. */
    public static Component parse(@Nullable String raw) {
        if (raw == null) {
            return Component.empty();
        }
        try {
            return MINI_MESSAGE.deserialize(raw);
        } catch (ParsingException e) {
            return Component.text(raw);
        }
    }

    /**
     * Replaces simple %placeholder% tokens then parses as MiniMessage.
     * Values are inserted literally (not re-parsed). Never returns null.
     */
    public static Component parse(@Nullable String raw, String... pairs) {
        if (raw == null) {
            return Component.empty();
        }
        String resolved = raw;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolved = resolved.replace(pairs[i], pairs[i + 1]);
        }
        return parse(resolved);
    }

    /** Serializes a component back to a MiniMessage string. Never returns null. */
    public static String stringify(Component component) {
        return MINI_MESSAGE.serialize(component);
    }
}
