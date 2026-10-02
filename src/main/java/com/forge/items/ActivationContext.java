package com.forge.items;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Everything an action needs to know about one activator firing.
 * Target and location may be null depending on the trigger.
 */
public record ActivationContext(
        Player player,
        CustomItem item,
        Activator activator,
        ItemStack stack,
        @Nullable Entity target,
        @Nullable Location location) {

    /** Location to use for world effects: explicit location, else target, else player. */
    public Location effectLocation() {
        if (location != null) {
            return location;
        }
        if (target != null) {
            return target.getLocation();
        }
        return player.getLocation();
    }

    /** Replaces %player% %target% %x% %y% %z% %world% placeholders. Never returns null. */
    public String resolve(@Nullable String raw) {
        if (raw == null) {
            return "";
        }
        String out = raw.replace("%player%", player.getName())
                .replace("%world%", player.getWorld().getName());
        Location loc = effectLocation();
        out = out.replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
        out = out.replace("%target%", target != null ? target.getName() : player.getName());
        return out;
    }
}
