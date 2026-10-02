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
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.event.Event.Result;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Wires all 25 ForgeItems triggers to Bukkit/Paper events, plus soulbound
 * handling and the LOOP scan task.
 */
public final class ItemListener implements Listener {
    private final ForgeItems plugin;
    private final ItemRegistry registry;
    private final CooldownManager cooldowns;
    private final ActionExecutor actions;
    private final Map<UUID, List<ItemStack>> soulboundStash = new HashMap<>();
    /** Throttle keys for cooldown/mana notices (avoids action-bar spam). */
    private final Map<String, Long> lastNotice = new HashMap<>();
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
            if (act.trigger() != trigger || !conditionsMet(player, act, target)) {
                continue;
            }
            UUID uuid = player.getUniqueId();
            String key = CooldownManager.key(item.id(), act.name());
            String globalKey = CooldownManager.key(item.id(), "global");
            double left = Math.max(
                    cooldowns.remaining(uuid, key, act.cooldownSeconds()),
                    cooldowns.remaining(uuid, globalKey, item.globalCooldownSeconds()));
            if (left > 0) {
                notifyCooldown(player, key, left);
                continue;
            }
            ManaManager mana = plugin.mana();
            if (act.manaCost() > 0 && mana.enabled() && !mana.has(player, act.manaCost())) {
                notifyNoMana(player, act);
                continue;
            }
            if (random.nextDouble() >= act.chance()) {
                continue;
            }
            cooldowns.set(uuid, key, act.cooldownSeconds());
            cooldowns.set(uuid, globalKey, item.globalCooldownSeconds());
            if (act.manaCost() > 0 && mana.enabled()) {
                mana.take(player, act.manaCost());
            }
            actions.execute(new ActivationContext(player, item, act, stack, target, location));
            fired = true;
            if (act.cancelEvent()) {
                cancel = true;
            }
            if (act.consumeUse() && item.usageLimit() > 0 && slot >= 0) {
                consumeUse(player, slot, stack, item);
            }
            if (item.levels() != null && slot >= 0) {
                int xp = item.levels().xpPerTrigger();
                if (trigger == Trigger.KILL_ENTITY) {
                    xp += item.levels().xpPerKill();
                } else if (trigger == Trigger.BLOCK_BREAK) {
                    xp += item.levels().xpPerBlockBreak();
                }
                if (xp > 0) {
                    plugin.levels().awardXp(player, slot, stack, item, xp);
                }
            }
        }
        return new FireResult(fired, cancel);
    }

    /** True when a notice was sent too recently (1.5s throttle per key). */
    private boolean throttled(String key) {
        if (lastNotice.size() > 2000) {
            lastNotice.clear();
        }
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(key);
        if (last != null && now - last < 1500) {
            return true;
        }
        lastNotice.put(key, now);
        return false;
    }

    private void notifyCooldown(Player player, String key, double secondsLeft) {
        if (!plugin.getConfig().getBoolean("settings.cooldown-notify", true)) {
            return;
        }
        if (throttled(player.getUniqueId() + ":" + key + ":cd")) {
            return;
        }
        player.sendActionBar(plugin.prefixedOr("messages.cooldown-left",
                "<gray>Ready in <white><seconds>s",
                "seconds", String.valueOf(Math.round(secondsLeft * 10.0) / 10.0)));
    }

    private void notifyNoMana(Player player, Activator act) {
        if (throttled(player.getUniqueId() + ":" + act.name() + ":mana")) {
            return;
        }
        player.sendMessage(plugin.prefixedOr("messages.no-mana",
                "<red>Not enough mana! <gray>(need <white><cost><gray>)",
                "cost", String.valueOf((int) act.manaCost())));
    }

    private boolean conditionsMet(Player player, Activator act, @Nullable Entity target) {
        if (act.sneaking() != null && player.isSneaking() != act.sneaking()) {
            return false;
        }
        if (act.permission() != null && !player.hasPermission(act.permission())) {
            return false;
        }
        double health = player.getHealth();
        if (health < act.minHealth() || health > act.maxHealth()) {
            return false;
        }
        if (!act.biomes().isEmpty()) {
            String biome = player.getLocation().getBlock().getBiome().getKey().toString()
                    .toLowerCase(Locale.ROOT);
            String shortBiome = biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
            if (!act.biomes().contains(biome) && !act.biomes().contains(shortBiome)) {
                return false;
            }
        }
        if (act.timeMode() != Activator.TimeMode.ANY) {
            long time = player.getWorld().getTime();
            boolean day = time < 12300 || time > 23850;
            if ((act.timeMode() == Activator.TimeMode.DAY) != day) {
                return false;
            }
        }
        if (act.weather() != Activator.WeatherMode.ANY) {
            boolean thunder = player.getWorld().isThundering();
            boolean storm = thunder || player.getWorld().hasStorm();
            switch (act.weather()) {
                case CLEAR -> {
                    if (storm) return false;
                }
                case RAIN -> {
                    if (!storm || thunder) return false;
                }
                case THUNDER -> {
                    if (!thunder) return false;
                }
                default -> { /* ANY handled above */ }
            }
        }
        int light = player.getLocation().getBlock().getLightLevel();
        if (light < act.minLight() || light > act.maxLight()) {
            return false;
        }
        if (!act.targetTypes().isEmpty()) {
            if (target == null || !act.targetTypes().contains(target.getType().name())) {
                return false;
            }
        }
        return true;
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
        Player player = event.getPlayer();
        if (event.isSneaking()) {
            scanSlots(player, Trigger.SNEAK_START, null, player.getLocation());
        }
        scanSlots(player, Trigger.SNEAK_TOGGLE, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemInHand();
        int slot = event.getHand() == EquipmentSlot.OFF_HAND ? 40
                : player.getInventory().getHeldItemSlot();
        FireResult result = tryActivate(player, slot, stack, Trigger.BLOCK_PLACE,
                null, event.getBlockPlaced().getLocation());
        if (result.cancel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = event.getItemDrop().getItemStack();
        FireResult result = tryActivate(player, -1, stack, Trigger.ITEM_DROP,
                event.getItemDrop(), event.getItemDrop().getLocation());
        if (result.cancel()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack stack = event.getItem().getItemStack();
        tryActivate(player, -1, stack, Trigger.ITEM_PICKUP,
                event.getItem(), event.getItem().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerDeathVictim(PlayerDeathEvent event) {
        Player player = event.getEntity();
        scanSlots(player, Trigger.PLAYER_DEATH, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack[] holder = new ItemStack[1];
        int slot = findHandSlot(player, holder);
        if (slot < 0) {
            return;
        }
        tryActivate(player, slot, holder[0], Trigger.FISH_CAUGHT,
                event.getCaught(), event.getHook().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSprintToggle(PlayerToggleSprintEvent event) {
        if (!event.isSprinting()) {
            return;
        }
        scanSlots(event.getPlayer(), Trigger.SPRINT_START, null, event.getPlayer().getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onGlideToggle(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }
        scanSlots(player, Trigger.GLIDE_START, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scanSlots(player, Trigger.PLAYER_JOIN, null, player.getLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRespawnScan(PlayerRespawnEvent event) {
        scanSlots(event.getPlayer(), Trigger.PLAYER_RESPAWN, null, event.getRespawnLocation());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        scanSlots(player, Trigger.WORLD_CHANGE, null, player.getLocation());
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
