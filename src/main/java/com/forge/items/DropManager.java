package com.forge.items;

import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;

/** Mob drop tables from drops.yml: custom items drop from mobs by chance. */
public final class DropManager implements Listener {
    /** One drop-table entry. Amounts are inclusive bounds. */
    public record MobDrop(EntityType mob, String itemId, double chance,
            int minAmount, int maxAmount, Set<String> biomes, Set<String> worlds) {
        public MobDrop {
            biomes = Set.copyOf(biomes);
            worlds = Set.copyOf(worlds);
        }
    }

    private final ItemRegistry registry;
    private final Random random = new Random();
    private List<MobDrop> drops = List.of();

    public DropManager(ItemRegistry registry) {
        this.registry = registry;
    }

    public void setDrops(List<MobDrop> drops) {
        this.drops = List.copyOf(drops);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Player || drops.isEmpty()) {
            return;
        }
        EntityType type = event.getEntityType();
        var loc = event.getEntity().getLocation();
        String biome = loc.getBlock().getBiome().getKey().toString().toLowerCase(Locale.ROOT);
        String shortBiome = biome.contains(":") ? biome.substring(biome.indexOf(':') + 1) : biome;
        String world = loc.getWorld().getName().toLowerCase(Locale.ROOT);
        for (MobDrop drop : drops) {
            if (drop.mob() != type) {
                continue;
            }
            if (!drop.worlds().isEmpty() && !drop.worlds().contains(world)) {
                continue;
            }
            if (!drop.biomes().isEmpty()
                    && !drop.biomes().contains(biome)
                    && !drop.biomes().contains(shortBiome)) {
                continue;
            }
            if (random.nextDouble() >= drop.chance()) {
                continue;
            }
            CustomItem def = registry.get(drop.itemId());
            if (def == null) {
                continue; // warned at load time
            }
            int amount = drop.minAmount() >= drop.maxAmount() ? drop.minAmount()
                    : drop.minAmount() + random.nextInt(drop.maxAmount() - drop.minAmount() + 1);
            event.getDrops().add(registry.build(def, amount));
        }
    }
}
