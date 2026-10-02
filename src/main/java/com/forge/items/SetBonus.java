package com.forge.items;

import java.util.List;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * One armor/item set from sets.yml with per-piece-count bonus tiers.
 * Immutable after construction.
 */
public record SetBonus(String id, String name, List<Tier> tiers) {

    /** A single bonus tier: granted while wearing at least {@code pieces} of the set. */
    public record Tier(int pieces, List<PotionEffect> effects, List<String> actions,
            List<String> commands, @Nullable String message) {
        public Tier {
            effects = List.copyOf(effects);
            actions = List.copyOf(actions);
            commands = List.copyOf(commands);
        }
    }

    public SetBonus {
        tiers = List.copyOf(tiers);
    }

    /** Highest tier whose piece requirement is met by {@code count}, or null. */
    public @Nullable Tier tierFor(int count) {
        Tier best = null;
        for (Tier tier : tiers) {
            if (count >= tier.pieces() && (best == null || tier.pieces() > best.pieces())) {
                best = tier;
            }
        }
        return best;
    }
}
