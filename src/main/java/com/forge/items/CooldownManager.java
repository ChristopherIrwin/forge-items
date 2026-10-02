package com.forge.items;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-item, per-activator cooldowns. All access happens on the
 * main thread; the concurrent map is just cheap insurance.
 */
public final class CooldownManager {
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    /** Cooldown key: item id + activator name (or "global" for item-wide). */
    public static String key(String itemId, String activatorName) {
        return itemId + ":" + activatorName;
    }

    /** Seconds remaining, or 0 when ready. */
    public double remaining(UUID player, String key, double cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return 0;
        }
        Long until = cooldowns.getOrDefault(player, Map.of()).get(key);
        if (until == null) {
            return 0;
        }
        double left = (until - System.currentTimeMillis()) / 1000.0;
        return Math.max(0, left);
    }

    public boolean ready(UUID player, String key, double cooldownSeconds) {
        return remaining(player, key, cooldownSeconds) <= 0;
    }

    public void set(UUID player, String key, double cooldownSeconds) {
        if (cooldownSeconds <= 0) {
            return;
        }
        cooldowns.computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(key, System.currentTimeMillis() + (long) (cooldownSeconds * 1000));
    }

    public void clear(UUID player) {
        cooldowns.remove(player);
    }
}
