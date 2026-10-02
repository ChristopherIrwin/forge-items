package com.forge.items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Paginated browser for all loaded custom items.
 * Clicking an item gives one copy to players with forgeitems.give.
 */
public final class ItemBrowserGui implements Listener {
    private static final int PAGE_SIZE = 45;

    private final ForgeItems plugin;
    private final Map<UUID, Integer> openPages = new HashMap<>();

    public ItemBrowserGui(ForgeItems plugin) {
        this.plugin = plugin;
    }

    /** Inventory marker carrying the viewed page. */
    private static final class Holder implements InventoryHolder {
        final int page;
        private @Nullable Inventory inventory;

        Holder(int page) {
            this.page = page;
        }

        @Override
        public @NotNull Inventory getInventory() {
            if (inventory == null) {
                throw new IllegalStateException("inventory not attached yet");
            }
            return inventory;
        }
    }

    /** Opens the browser on the given page (0-based, clamped). */
    public void open(Player player, int page) {
        List<CustomItem> items = sorted();
        int pages = Math.max(1, (items.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int p = Math.max(0, Math.min(page, pages - 1));
        Holder holder = new Holder(p);
        Inventory inv = Bukkit.createInventory(holder, 54,
                TextUtil.parse("<gold>ForgeItems <gray>— page " + (p + 1) + "/" + pages));
        holder.inventory = inv;
        int start = p * PAGE_SIZE;
        for (int i = 0; i < PAGE_SIZE && start + i < items.size(); i++) {
            inv.setItem(i, plugin.registry().build(items.get(start + i), 1));
        }
        if (p > 0) {
            inv.setItem(45, navItem(Material.ARROW, "<yellow>← Previous page"));
        }
        inv.setItem(49, navItem(Material.BARRIER, "<red>Close"));
        if (p < pages - 1) {
            inv.setItem(53, navItem(Material.ARROW, "<yellow>Next page →"));
        }
        openPages.put(player.getUniqueId(), p);
        player.openInventory(inv);
    }

    private List<CustomItem> sorted() {
        List<CustomItem> list = new ArrayList<>();
        for (String id : plugin.registry().ids()) {
            CustomItem item = plugin.registry().get(id);
            if (item != null) {
                list.add(item);
            }
        }
        list.sort(Comparator.comparingInt((CustomItem i) -> i.rarity().ordinal())
                .reversed()
                .thenComparing(CustomItem::id));
        return list;
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == 45 && holder.page > 0) {
            open(player, holder.page - 1);
            return;
        }
        if (slot == 53) {
            open(player, holder.page + 1);
            return;
        }
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        if (slot >= PAGE_SIZE) {
            return;
        }
        if (!player.hasPermission("forgeitems.give")) {
            player.sendMessage(plugin.prefixed("messages.no-permission"));
            return;
        }
        CustomItem def = plugin.registry().getCustomItem(clicked);
        if (def == null) {
            return;
        }
        var leftover = player.getInventory().addItem(plugin.registry().build(def, 1));
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
        player.sendMessage(plugin.prefixed("messages.item-received",
                "amount", "1", "name", plugin.plainName(def)));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            openPages.remove(event.getPlayer().getUniqueId());
        }
    }
}
