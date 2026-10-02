package com.forge.items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.Event.Result;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wires all 14 ForgeItems triggers to Bukkit/Paper events, plus soulbound
 * handling and the LOOP scan task.
 */
public final class ItemListener implements Listener {
    private final ForgeItems plugin;
    private final ItemRegistry registry;
    private final CooldownManager cooldowns;
    private final ActionExecutor actions;
    private final Map<UUID, List<ItemStack>> soulboundStash = new HashMap<>();
    private final Random random = new Random();

    /** Result of attempting to fire activators for one trigger. */
    public record FireResult(boolean fired, boolean cancel) {}

    public ItemListener(ForgeItems plugin, ItemRegistry registry,
            CooldownManager cooldowns, ActionExecutor actions) {
        this.plugin = plugin;
        this.registry = registry;
        this.cooldowns = cooldowns;
        this.actions = actions;
    }

    /** Starts the single shared LOOP scan task (never one task per player). */
    public void startLoopTask() {
        int interval = plugin.getConfig().getInt("settings.loop-interval-ticks", 20);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                scanSlots(player, Trigger.LOOP, null, null);
            }
        }, interval, interval);
    }

    // ------------------------------------------------------------ core firing

    private FireResult tryActivate(Player player, int slot, ItemStack stack,
            Trigger trigger, @Nullable Entity target, @Nullable Location location) {
        CustomItem item = registry.getCustomItem(stack);
        if (item == null) {
            return new FireResult(false, false);
        }
        if (item.restrictedWorlds().contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) {
            return new FireResult(false, false);
        }
        String usePerm = item.effectiveUsePermission();
        if (usePerm != null && !player.hasPermission(usePerm)) {
            return new FireResult(false, false);
        }
        boolean fired = false;
        boolean cancel = false;
        for (Activator act : item.activators().values()) {
            if (act.trigger() != trigger || !conditionsMet(player, act)) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            String key = CooldownManager.key(item.id(), act.name());
            String globalKey = CooldownManager.key(item.id(), "global");
            if (!cooldowns.ready(uuid, key, act.cooldownSeconds())
                    || !cooldowns.ready(uuid, globalKey, item.globalCooldownSeconds())) {
                continue;
            }
            if (random.nextDouble() >= act.chance()) {
                continue;
            }
            cooldowns.set(uuid, key, act.cooldownSeconds());
            cooldowns.set(uuid, globalKey, item.globalCooldownSeconds());
            actions.execute(new ActivationContext(player, item, act, stack, target, location));
            fired = true;
            if (act.cancelEvent()) {
                cancel = true;
            }
            if (act.consumeUse() && item.usageLimit() > 0 && slot >= 0) {
                consumeUse(player, slot, stack, item);
            }
        }
        return new FireResult(fired, cancel);
    }

    private boolean conditionsMet(Player player, Activator act) {
        if (act.sneaking() != null && player.isSneaking() != act.sneaking()) {
            return false;
        }
        if (act.permission() != null && !player.hasPermission(act.permission())) {
            return false;
        }
        double health = player.getHealth();
        return health >= act.minHealth() && health <= act.maxHealth();
    }

    /** Decrements the PDC usage counter; breaks the item at zero. */
    private void consumeUse(Player player, int slot, ItemStack stack, CustomItem item) {
        var meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Integer left = meta.getPersistentDataContainer().get(registry.usesKey(), PersistentDataType.INTEGER);
        if (left == null) {
            return;
        }
        int remaining = left - 1;
        if (remaining <= 0) {
            player.getInventory().setItem(slot, null);
            String name = plugin.plainName(item);
            player.sendMessage(plugin.prefixed("messages.item-broken", "name", name));
            player.playSound(Sound.sound(Key.key("minecraft:entity.item.break"),
                    Sound.Source.PLAYER, 1.0f, 1.0f));
        } else {
            meta.getPersistentDataContainer().set(registry.usesKey(), PersistentDataType.INTEGER, remaining);
            stack.setItemMeta(meta);
            player.getInventory().setItem(slot, stack);
            if (remaining <= 5) {
                player.sendMessage(plugin.prefixed("messages.usage-left",
                        "uses", String.valueOf(remaining), "name", plugin.plainName(item)));
            }
        }
    }

    /** Scans armor + hands for a trigger (used by LOOP, SNEAK_TOGGLE, TAKE_DAMAGE). */
    private boolean scanSlots(Player player, Trigger trigger, @Nullable Entity target,
            @Nullable Location location) {
        var inv = player.getInventory();
        int[] slots = {36, 37, 38, 39, inv.getHeldItemSlot(), 40};
        boolean cancel = false;
        for (int slot : slots) {
            ItemStack stack = inv.getItem(slot);
            if (stack == null) {
                continue;
            }
            if (tryActivate(player, slot, stack, trigger, target, location).cancel()) {
                cancel = true;
            }
        }
        return cancel;
    }

    /** Finds a ForgeItems stack in either hand; returns slot index or -1. */
    private int findHandSlot(Player player, ItemStack[] out) {
        var inv = player.getInventory();
        ItemStack main = inv.getItemInMainHand();
        if (registry.getCustomItem(main) != null) {
            out[0] = main;
            return inv.getHeldItemSlot();
        }
        ItemStack off = inv.getItemInOffHand();
        if (registry.getCustomItem(off) != null) {
            out[0] = off;
            return 40;
        }
        return -1;
    }

    /**
     * Resolves the attacking player, or null when the damager isn't one
     * (also accepts null, e.g. a DamageSource with no causing entity).
     */
    private @Nullable Player resolveAttacker(@Nullable Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null) {
            return;
        }
        Action action = event.getAction();
        boolean shift = event.getPlayer().isSneaking();
        Trigger trigger;
        if (shift && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            trigger = Trigger.SHIFT_RIGHT_CLICK;
        } else if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            trigger = Trigger.RIGHT_CLICK;
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            trigger = Trigger.LEFT_CLICK;
        } else {
            return;
        }
        EquipmentSlot hand = event.getHand();
        int slot = hand == EquipmentSlot.OFF_HAND ? 40
                : event.getPlayer().getInventory().getHeldItemSlot();
        Location loc = event.getClickedBlock() != null
                ? event.getClickedBlock().getLocation() : event.getPlayer().getLocation();
        FireResult result = tryActivate(event.getPlayer(), slot, stack, trigger, null, loc);
        if (result.cancel()) {
            // Never call the deprecated isCancelled(); deny via the use-flags.
            event.setUseInteractedBlock(Result.DENY);
            event.setUseItemInHand(Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onHitEntity(EntityDamageByEntityEvent event) {
        Player player = resolveAttacker(event.getDamager());
        if (player == null) {
            return;
        }
        var inv = player.getInventory();
        tryActivate(player, inv.getHeldItemSlot(), inv.getItemInMainHand(),
                Trigger.HIT_ENTITY, event.getEntity(), event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onKillEntity(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player) {
            return; // player deaths are handled by the soulbound handler below
        }
        Player player = resolveAttacker(event.getDamageSource().getCausingEntity());
        if (player == null) {
            return;
        }
        var inv = player.getInventory();
        tryActivate(player, inv.getHeldItemSlot(), inv.getItemInMainHand(),
                Trigger.KILL_ENTITY, event.getEntity(), event.getEntity().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        var inv = player.getInventory();
        FireResult result = tryActivate(player, inv.getHeldItemSlot(), inv.getItemInMainHand(),
                Trigger.BLOCK_BREAK, null, event.getBlock().getLocation());
        if (result.cancel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onTakeDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        boolean cancel = scanSlots(player, Trigger.TAKE_DAMAGE, null, player.getLocation());
        if (cancel) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onArmorChange(PlayerArmorChangeEvent event) {
        Player player = event.getPlayer();
        int slot = switch (event.getSlot()) {
            case HEAD -> 39;
            case CHEST -> 38;
            case LEGS -> 37;
            case FEET -> 36;
            default -> -1;
        };
        if (!event.getOldItem().getType().isAir()) {
            // slot -1: the item already left the inventory, so usage write-back is skipped
            tryActivate(player, -1, event.getOldItem(), Trigger.UNEQUIP, null, null);
        }
        if (!event.getNewItem().getType().isAir()) {
            tryActivate(player, slot, event.getNewItem(), Trigger.EQUIP, null, null);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack[] holder = new ItemStack[1];
        int slot = findHandSlot(player, holder);
        if (slot < 0) {
            return;
        }
        tryActivate(player, slot, holder[0], Trigger.CONSUME, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        ItemStack[] holder = new ItemStack[1];
        int slot = findHandSlot(player, holder);
        if (slot < 0) {
            return;
        }
        tryActivate(player, slot, holder[0], Trigger.PROJECTILE_LAUNCH, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player player)) {
            return;
        }
        ItemStack[] holder = new ItemStack[1];
        int slot = findHandSlot(player, holder);
        if (slot < 0) {
            return;
        }
        Location loc = event.getHitBlock() != null ? event.getHitBlock().getLocation()
                : event.getHitEntity() != null ? event.getHitEntity().getLocation()
                : event.getEntity().getLocation();
        tryActivate(player, slot, holder[0], Trigger.PROJECTILE_HIT, event.getHitEntity(), loc);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSneakToggle(PlayerToggleSneakEvent event) {
        scanSlots(event.getPlayer(), Trigger.SNEAK_TOGGLE, null, event.getPlayer().getLocation());
    }

    // -------------------------------------------------------------- soulbound

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        List<ItemStack> stashed = new ArrayList<>();
        var drops = event.getDrops().listIterator();
        while (drops.hasNext()) {
            ItemStack drop = drops.next();
            CustomItem item = registry.getCustomItem(drop);
            if (item != null && item.keepOnDeath()) {
                stashed.add(drop);
                drops.remove();
            }
        }
        if (!stashed.isEmpty()) {
            soulboundStash.put(player.getUniqueId(), stashed);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        List<ItemStack> stashed = soulboundStash.remove(event.getPlayer().getUniqueId());
        if (stashed == null) {
            return;
        }
        for (ItemStack stack : stashed) {
            var leftover = event.getPlayer().getInventory().addItem(stack);
            for (ItemStack rest : leftover.values()) {
                event.getPlayer().getWorld().dropItemNaturally(event.getPlayer().getLocation(), rest);
            }
        }
    }
}
