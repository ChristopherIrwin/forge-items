package com.forge.items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Per-player mana pool. Activators can cost mana; it regenerates over time.
 * All access happens on the main thread; the concurrent map is cheap insurance.
 */
public final class ManaManager {
    private final ForgeItems plugin;
    private final Map<UUID, Double> mana = new ConcurrentHashMap<>();

    public ManaManager(ForgeItems plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("settings.mana.enabled", true);
    }

    public double maxMana(Player player) {
        return Math.max(1, plugin.getConfig().getDouble("settings.mana.max-mana", 100.0));
    }

    /** Starts the regen + display task (once per second). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        if (!enabled()) {
            return;
        }
        double regen = Math.max(0, plugin.getConfig().getDouble("settings.mana.regen-per-second", 5.0));
        String display = plugin.getConfig().getString("settings.mana.display", "actionbar");
        String format = plugin.getConfig().getString("settings.mana.display-format",
                "<aqua>Mana <white>%mana%<gray>/%max%");
        for (Player player : Bukkit.getOnlinePlayers()) {
            double max = maxMana(player);
            double current = Math.min(max, get(player) + regen);
            mana.put(player.getUniqueId(), current);
            if ("actionbar".equalsIgnoreCase(display) && current < max) {
                player.sendActionBar(TextUtil.parse(format
                        .replace("%mana%", String.valueOf((int) current))
                        .replace("%max%", String.valueOf((int) max))));
            }
        }
    }

    /** Current mana (full when never tracked). */
    public double get(Player player) {
        return mana.getOrDefault(player.getUniqueId(), maxMana(player));
    }

    public boolean has(Player player, double amount) {
        return get(player) >= amount;
    }

    /** Deducts mana; returns false when insufficient (nothing deducted). */
    public boolean take(Player player, double amount) {
        if (!has(player, amount)) {
            return false;
        }
        mana.put(player.getUniqueId(), get(player) - amount);
        return true;
    }

    /** Restores mana, clamped to the maximum. */
    public void give(Player player, double amount) {
        mana.put(player.getUniqueId(), Math.min(maxMana(player), get(player) + amount));
    }

    /** Forgets a player's pool (they regenerate from full). */
    public void reset(Player player) {
        mana.remove(player.getUniqueId());
    }
}
