package com.forge.items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;

/**
 * Tracks worn set pieces and applies/removes set bonus tiers.
 * Checks run periodically plus immediately on armor changes.
 */
public final class SetBonusManager implements Listener {
    /** Seconds between bonus reward (message/actions) re-fires while a tier stays active. */
    private static final double REWARD_COOLDOWN_SECONDS = 30.0;

    private final ForgeItems plugin;
    private final ItemRegistry registry;
    private final ActionExecutor actions;
    private final CooldownManager cooldowns;
    /** Player -> (set id -> active tier pieces, 0 when none). */
    private final Map<UUID, Map<String, Integer>> active = new HashMap<>();

    public SetBonusManager(ForgeItems plugin, ItemRegistry registry,
            ActionExecutor actions, CooldownManager cooldowns) {
        this.plugin = plugin;
        this.registry = registry;
        this.actions = actions;
        this.cooldowns = cooldowns;
    }

    /** Starts the periodic set scan (runs on the main thread). */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, 40L, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        checkPlayer(event.getPlayer());
    }

    private void checkAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            checkPlayer(player);
        }
    }

    /** Recomputes one player's set tiers; applies/removes/fires on change. */
    public void checkPlayer(Player player) {
        Map<String, Integer> counts = countSets(player);
        Map<String, Integer> was = active.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>());
        for (SetBonus bonus : registry.setBonuses().values()) {
            int count = counts.getOrDefault(bonus.id(), 0);
            SetBonus.Tier nowTier = bonus.tierFor(count);
            int nowPieces = nowTier == null ? 0 : nowTier.pieces();
            int wasPieces = was.getOrDefault(bonus.id(), 0);
            if (nowPieces == wasPieces) {
                continue;
            }
            if (wasPieces > 0) {
                SetBonus.Tier oldTier = bonus.tierFor(wasPieces);
                if (oldTier != null) {
                    removeTier(player, oldTier);
                }
            }
            if (nowTier != null) {
                applyTier(player, nowTier);
                fireTierReward(player, bonus, nowTier);
            }
            if (nowPieces == 0) {
                was.remove(bonus.id());
            } else {
                was.put(bonus.id(), nowPieces);
            }
        }
        was.keySet().removeIf(id -> !counts.containsKey(id) && !registry.setBonuses().containsKey(id));
    }

    /** Counts worn pieces per set id across the four armor slots. */
    private Map<String, Integer> countSets(Player player) {
        Map<String, Integer> counts = new HashMap<>();
        var inv = player.getInventory();
        ItemStack[] armor = inv.getArmorContents();
        for (ItemStack stack : armor) {
            CustomItem item = registry.getCustomItem(stack);
            if (item == null || item.setId() == null) {
                continue;
            }
            counts.merge(item.setId(), 1, Integer::sum);
        }
        return counts;
    }

    private void applyTier(Player player, SetBonus.Tier tier) {
        for (PotionEffect effect : tier.effects()) {
            player.addPotionEffect(effect);
        }
    }

    private void removeTier(Player player, SetBonus.Tier tier) {
        for (PotionEffect effect : tier.effects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    /** Fires a tier's message/commands/actions once per threshold crossing (30s re-fire guard). */
    private void fireTierReward(Player player, SetBonus bonus, SetBonus.Tier tier) {
        String key = "setbonus:" + bonus.id() + ":" + tier.pieces();
        if (!cooldowns.ready(player.getUniqueId(), key, REWARD_COOLDOWN_SECONDS)) {
            return;
        }
        cooldowns.set(player.getUniqueId(), key, REWARD_COOLDOWN_SECONDS);
        if (tier.message() != null) {
            player.sendMessage(TextUtil.parse(tier.message()));
        }
        if (tier.actions().isEmpty() && tier.commands().isEmpty()) {
            return;
        }
        ItemStack piece = findWornPiece(player, bonus.id());
        if (piece == null) {
            return;
        }
        CustomItem def = registry.getCustomItem(piece);
        if (def == null) {
            return;
        }
        Activator synthetic = new Activator(
                "set-bonus", Trigger.SET_BONUS, 0, 1.0, false, false,
                null, null, 0, Double.MAX_VALUE,
                java.util.Set.of(), Activator.TimeMode.ANY, Activator.WeatherMode.ANY,
                0, 15, java.util.Set.of(),
                tier.actions(), tier.commands(), null, null, List.of());
        actions.execute(new ActivationContext(player, def, synthetic, piece, null, null));
    }

    private @Nullable ItemStack findWornPiece(Player player, String setId) {
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            CustomItem item = registry.getCustomItem(stack);
            if (item != null && setId.equals(item.setId())) {
                return stack;
            }
        }
        return null;
    }
}
