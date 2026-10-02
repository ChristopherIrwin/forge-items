package com.forge.items;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * In-game item editor (/fitems edit &lt;id&gt;, /fitems create &lt;id&gt;).
 * Mutates the raw YAML through the registry, then saves and reloads.
 * Text/number entry goes through ChatInput sessions.
 */
public final class ItemEditorGui implements Listener {
    private final ForgeItems plugin;
    private final ChatInput chat;
    /** Players who clicked delete once (second click confirms). */
    private final Set<UUID> deleteArmed = new HashSet<>();

    public ItemEditorGui(ForgeItems plugin, ChatInput chat) {
        this.plugin = plugin;
        this.chat = chat;
    }

    // ------------------------------------------------------------ holders

    private static final class ItemHolder implements InventoryHolder {
        final String id;
        private @Nullable Inventory inventory;
        ItemHolder(String id) { this.id = id; }
        @Override public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("not attached");
            return inventory;
        }
    }

    private static final class ActivatorHolder implements InventoryHolder {
        final String id;
        final String activator;
        private @Nullable Inventory inventory;
        ActivatorHolder(String id, String activator) { this.id = id; this.activator = activator; }
        @Override public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("not attached");
            return inventory;
        }
    }

    private static final class PickerHolder implements InventoryHolder {
        final String id;
        /** Null when picking for a brand-new activator, else the activator being changed. */
        final @Nullable String activator;
        private @Nullable Inventory inventory;
        PickerHolder(String id, @Nullable String activator) { this.id = id; this.activator = activator; }
        @Override public @NotNull Inventory getInventory() {
            if (inventory == null) throw new IllegalStateException("not attached");
            return inventory;
        }
    }

    // ------------------------------------------------------------ item screen

    /** Opens the main editor for an item. */
    public void openItem(Player player, String id) {
        CustomItem def = plugin.registry().get(id);
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (def == null || cfg == null) {
            player.sendMessage(plugin.prefixed("messages.unknown-item", "id", id));
            return;
        }
        ItemHolder holder = new ItemHolder(def.id());
        Inventory inv = Bukkit.createInventory(holder, 54,
                TextUtil.parse("<gold>Editing: <white>" + def.id()));
        holder.inventory = inv;

        inv.setItem(4, plugin.registry().build(def, 1));

        inv.setItem(10, button(Material.NAME_TAG, "<yellow>Name",
                List.of("<gray>Current: <white>" + orNone(cfg.getString("name")),
                        "<gray>Click to set via chat")));
        int loreLines = cfg.getStringList("lore").size();
        inv.setItem(11, button(Material.WRITABLE_BOOK, "<yellow>Lore <gray>(" + loreLines + " lines)",
                List.of("<gray>Left-click: add line", "<gray>Right-click: remove last line",
                        "<gray>Shift-click: clear all")));
        inv.setItem(12, button(Material.ANVIL, "<yellow>Material: <white>" + def.material(),
                List.of("<gray>Click: copy held item's material")));
        inv.setItem(13, button(Material.NETHER_STAR, "<yellow>Rarity: " + def.rarity().loreLine(),
                List.of("<gray>Click to cycle")));
        inv.setItem(14, button(Material.DIAMOND_CHESTPLATE,
                "<yellow>Set: <white>" + (def.setId() == null ? "(none)" : def.setId()),
                List.of("<gray>Click to cycle")));
        inv.setItem(15, button(Material.ENCHANTED_BOOK,
                "<yellow>Enchantments <gray>(" + def.enchantments().size() + ")",
                List.of("<gray>Left-click: copy held item's", "<gray>Right-click: clear all")));
        inv.setItem(16, button(Material.PAPER, "<yellow>Use permission",
                List.of("<gray>Current: <white>" + orNone(cfg.getString("use-permission")),
                        "<gray>Click to set via chat", "<gray>(empty clears)")));

        inv.setItem(19, toggleButton(Material.BEDROCK, "Unbreakable", cfg.getBoolean("unbreakable", false)));
        inv.setItem(20, toggleButton(Material.TOTEM_OF_UNDYING, "Keep on death",
                cfg.getBoolean("keep-on-death", false)));
        inv.setItem(21, button(Material.CHEST, "<yellow>Max stack size: <white>" + def.maxStackSize(),
                List.of("<gray>Click to set via chat", "<gray>0 = vanilla default")));
        inv.setItem(22, button(Material.DIAMOND_SWORD, "<yellow>Usage limit: <white>"
                + (def.usageLimit() <= 0 ? "unlimited" : def.usageLimit()),
                List.of("<gray>Click to set via chat", "<gray>0 = unlimited")));
        inv.setItem(23, button(Material.CLOCK, "<yellow>Global cooldown: <white>"
                + def.globalCooldownSeconds() + "s", List.of("<gray>Click to set via chat")));
        inv.setItem(24, toggleButton(Material.COOKIE, "Consumable", cfg.getBoolean("consumable", false)));

        List<String> activators = new ArrayList<>(def.activators().keySet());
        activators.sort(String::compareTo);
        int slot = 28;
        for (String name : activators) {
            if (slot > 43) break;
            Activator act = def.activators().get(name);
            inv.setItem(slot++, button(Material.PAPER, "<yellow>" + name,
                    List.of("<gray>Trigger: <white>" + act.trigger(),
                            "<gray>Cooldown: <white>" + act.cooldownSeconds() + "s",
                            "<gray>Chance: <white>" + act.chance(),
                            "<gray>Mana: <white>" + act.manaCost(),
                            "<gray>Actions: <white>" + act.actions().size(),
                            "",
                            "<gray>Left-click: edit",
                            "<red>Right-click: delete")));
        }

        inv.setItem(47, button(Material.EMERALD, "<green>Add activator",
                List.of("<gray>Pick a trigger, then configure it")));
        inv.setItem(49, button(Material.EMERALD_BLOCK, "<green>Save & reload",
                List.of("<gray>Writes items/" + def.id() + ".yml")));
        inv.setItem(51, button(Material.TNT, "<red>Delete item",
                List.of("<gray>Deletes the file", "<red>Click twice to confirm")));
        inv.setItem(53, button(Material.BARRIER, "<red>Close", List.of()));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------ activator screen

    /** Opens the editor for one activator. */
    public void openActivator(Player player, String id, String activatorName) {
        CustomItem def = plugin.registry().get(id);
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (def == null || cfg == null) {
            return;
        }
        Activator act = def.activators().get(activatorName.toLowerCase(Locale.ROOT));
        if (act == null) {
            openItem(player, id);
            return;
        }
        String base = "activators." + act.name();
        ActivatorHolder holder = new ActivatorHolder(def.id(), act.name());
        Inventory inv = Bukkit.createInventory(holder, 54,
                TextUtil.parse("<gold>Activator: <white>" + act.name()));
        holder.inventory = inv;

        inv.setItem(4, button(Material.PAPER, "<yellow>" + act.name(),
                List.of("<gray>Item: <white>" + def.id())));
        inv.setItem(10, button(Material.COMPARATOR, "<yellow>Trigger: <white>" + act.trigger(),
                List.of("<gray>Click to change")));
        inv.setItem(11, button(Material.CLOCK, "<yellow>Cooldown: <white>" + act.cooldownSeconds() + "s",
                List.of("<gray>Click to set via chat")));
        inv.setItem(12, button(Material.ENDER_PEARL, "<yellow>Chance: <white>" + act.chance(),
                List.of("<gray>Click to set via chat", "<gray>0.0 - 1.0")));
        inv.setItem(13, button(Material.LAPIS_LAZULI, "<yellow>Mana cost: <white>" + act.manaCost(),
                List.of("<gray>Click to set via chat")));
        inv.setItem(14, toggleButton(Material.REDSTONE, "Cancel event", act.cancelEvent()));
        inv.setItem(15, toggleButton(Material.HOPPER, "Consume use", act.consumeUse()));
        inv.setItem(16, button(Material.PAPER, "<yellow>Message",
                List.of("<gray>Current: <white>" + orNone(cfg.getString(base + ".message")),
                        "<gray>Click to set via chat", "<gray>(empty clears)")));

        List<String> actions = cfg.getStringList(base + ".actions");
        for (int i = 0; i < 7 && i < actions.size(); i++) {
            inv.setItem(19 + i, button(Material.WRITABLE_BOOK, "<yellow>Action " + (i + 1),
                    List.of("<white>" + actions.get(i), "", "<red>Click to remove")));
        }
        if (actions.size() > 7) {
            inv.setItem(26, button(Material.PAPER, "<gray>+" + (actions.size() - 7) + " more",
                    List.of("<gray>Edit the YAML for long lists")));
        } else {
            inv.setItem(26, button(Material.EMERALD, "<green>Add action",
                    List.of("<gray>Enter a verb line via chat")));
        }

        List<String> commands = cfg.getStringList(base + ".commands");
        for (int i = 0; i < 7 && i < commands.size(); i++) {
            inv.setItem(28 + i, button(Material.COMMAND_BLOCK, "<yellow>Command " + (i + 1),
                    List.of("<white>" + commands.get(i), "", "<red>Click to remove")));
        }
        if (commands.size() > 7) {
            inv.setItem(35, button(Material.PAPER, "<gray>+" + (commands.size() - 7) + " more",
                    List.of("<gray>Edit the YAML for long lists")));
        } else {
            inv.setItem(35, button(Material.EMERALD, "<green>Add command",
                    List.of("<gray>Console command via chat", "<gray>%player% supported")));
        }

        List<String> effects = effectStrings(cfg, base);
        for (int i = 0; i < 7 && i < effects.size(); i++) {
            inv.setItem(37 + i, button(Material.POTION, "<yellow>Effect " + (i + 1),
                    List.of("<white>" + effects.get(i), "", "<red>Click to remove")));
        }
        if (effects.size() > 7) {
            inv.setItem(44, button(Material.PAPER, "<gray>+" + (effects.size() - 7) + " more",
                    List.of("<gray>Edit the YAML for long lists")));
        } else {
            inv.setItem(44, button(Material.EMERALD, "<green>Add effect",
                    List.of("<gray>Format: <white>type duration amplifier")));
        }

        inv.setItem(47, button(Material.ARROW, "<yellow>Back to item", List.of()));
        inv.setItem(49, button(Material.EMERALD_BLOCK, "<green>Save & reload", List.of()));
        inv.setItem(53, button(Material.BARRIER, "<red>Close", List.of()));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------ trigger picker

    /** Opens the trigger picker; activator null = creating a new activator. */
    public void openPicker(Player player, String id, @Nullable String activator) {
        PickerHolder holder = new PickerHolder(id, activator);
        Inventory inv = Bukkit.createInventory(holder, 27,
                TextUtil.parse("<gold>Pick a trigger"));
        holder.inventory = inv;
        int slot = 0;
        for (Trigger trigger : Trigger.values()) {
            if (trigger == Trigger.SET_BONUS || trigger == Trigger.LEVEL_UP) {
                continue; // synthetic-only
            }
            inv.setItem(slot++, button(Material.PAPER, "<yellow>" + trigger.name(),
                    List.of("<gray>" + TRIGGER_DESCRIPTIONS.getOrDefault(trigger, ""))));
        }
        inv.setItem(26, button(Material.ARROW, "<yellow>Back", List.of()));
        player.openInventory(inv);
    }

    // ------------------------------------------------------------ click routing

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory().getHolder() instanceof ItemHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 54) {
                handleItemClick(player, holder.id, event.getRawSlot(), event.getClick());
            }
        } else if (event.getInventory().getHolder() instanceof ActivatorHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 54) {
                handleActivatorClick(player, holder.id, holder.activator,
                        event.getRawSlot(), event.getClick());
            }
        } else if (event.getInventory().getHolder() instanceof PickerHolder holder) {
            event.setCancelled(true);
            if (event.getRawSlot() < 27) {
                handlePickerClick(player, holder.id, holder.activator, event.getRawSlot());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof ItemHolder
                || event.getInventory().getHolder() instanceof ActivatorHolder) {
            deleteArmed.remove(event.getPlayer().getUniqueId());
        }
    }

    /** Re-fetches the live raw config; closes the inventory when the item vanished. */
    private @Nullable YamlConfiguration liveConfig(Player player, String id) {
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) {
            player.closeInventory();
        }
        return cfg;
    }

    // ------------------------------------------------------------ item clicks

    private void handleItemClick(Player player, String id,
            int slot, ClickType click) {
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 10 -> chat.request(player, "<yellow>Enter the new display name (MiniMessage):",
                    answer -> {
                        if (answer == null) { openItem(player, id); return; }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        live.set("name", answer.isEmpty() ? null : answer);
                        refreshItem(player, id);
                    });
            case 11 -> {
                if (click.isShiftClick()) {
                    cfg.set("lore", new ArrayList<String>());
                    refreshItem(player, id);
                } else if (click.isRightClick()) {
                    List<String> lore = cfg.getStringList("lore");
                    if (!lore.isEmpty()) {
                        lore.remove(lore.size() - 1);
                        cfg.set("lore", lore);
                    }
                    refreshItem(player, id);
                } else {
                    chat.request(player, "<yellow>Enter a lore line (MiniMessage):",
                            answer -> {
                                if (answer == null) { openItem(player, id); return; }
                                YamlConfiguration live = liveConfig(player, id);
                                if (live == null) return;
                                List<String> lore = live.getStringList("lore");
                                lore.add(answer);
                                live.set("lore", lore);
                                refreshItem(player, id);
                            });
                }
            }
            case 12 -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType().isAir()) {
                    player.sendMessage(plugin.prefixedOr("messages.editor-empty-hand",
                            "<red>Hold an item in your main hand first."));
                } else {
                    cfg.set("material", held.getType().name());
                    player.sendMessage(plugin.prefixedOr("messages.editor-material-set",
                            "<green>Material set to <white><material>.",
                            "material", held.getType().name()));
                }
                refreshItem(player, id);
            }
            case 13 -> {
                Rarity[] values = Rarity.values();
                Rarity current = Rarity.fromString(cfg.getString("rarity", "COMMON"));
                if (current == null) current = Rarity.COMMON;
                cfg.set("rarity", values[(current.ordinal() + 1) % values.length].name());
                refreshItem(player, id);
            }
            case 14 -> {
                List<String> options = new ArrayList<>();
                options.add("(none)");
                options.addAll(plugin.registry().setBonuses().keySet().stream().sorted().toList());
                String current = cfg.getString("set", "(none)");
                int next = (options.indexOf(current) + 1) % options.size();
                String picked = options.get(next < 0 ? 0 : next);
                cfg.set("set", "(none)".equals(picked) ? null : picked);
                refreshItem(player, id);
            }
            case 15 -> {
                if (click.isRightClick()) {
                    cfg.set("enchantments", null);
                    refreshItem(player, id);
                } else {
                    ItemStack held = player.getInventory().getItemInMainHand();
                    var meta = held.getItemMeta();
                    if (meta == null || meta.getEnchants().isEmpty()) {
                        player.sendMessage(plugin.prefixedOr("messages.editor-no-enchants",
                                "<red>Your held item has no enchantments."));
                        openItem(player, id);
                    } else {
                        for (var entry : meta.getEnchants().entrySet()) {
                            cfg.set("enchantments." + entry.getKey().getKey().toString(),
                                    entry.getValue());
                        }
                        refreshItem(player, id);
                    }
                }
            }
            case 16 -> chat.request(player, "<yellow>Enter the use permission (empty clears):",
                    answer -> {
                        if (answer == null) { openItem(player, id); return; }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        live.set("use-permission", answer.isEmpty() ? null : answer);
                        refreshItem(player, id);
                    });
            case 19 -> { cfg.set("unbreakable", !cfg.getBoolean("unbreakable", false)); refreshItem(player, id); }
            case 20 -> { cfg.set("keep-on-death", !cfg.getBoolean("keep-on-death", false)); refreshItem(player, id); }
            case 21 -> askDouble(player, id, "<yellow>Enter max stack size (0 = vanilla default):",
                    value -> cfg.set("max-stack-size", Math.max(0, (int) Math.round(value))),
                    () -> openItem(player, id));
            case 22 -> askDouble(player, id, "<yellow>Enter usage limit (0 = unlimited):",
                    value -> cfg.set("usage-limit", Math.max(0, (int) Math.round(value))),
                    () -> openItem(player, id));
            case 23 -> askDouble(player, id, "<yellow>Enter global cooldown in seconds:",
                    value -> cfg.set("cooldown-seconds", Math.max(0, value)),
                    () -> openItem(player, id));
            case 24 -> { cfg.set("consumable", !cfg.getBoolean("consumable", false)); refreshItem(player, id); }
            case 47 -> openPicker(player, id, null);
            case 49 -> {
                refreshItem(player, id);
                player.sendMessage(plugin.prefixed("messages.reloaded",
                        "count", String.valueOf(plugin.registry().ids().size())));
            }
            case 51 -> {
                if (deleteArmed.remove(player.getUniqueId())) {
                    player.closeInventory();
                    if (plugin.registry().deleteItem(id)) {
                        plugin.reloadItems();
                        player.sendMessage(plugin.prefixedOr("messages.editor-deleted",
                                "<red>Deleted item <white><id><red>.", "id", id));
                    } else {
                        player.sendMessage(plugin.prefixedOr("messages.editor-delete-failed",
                                "<red>Could not delete <white><id><red>.", "id", id));
                    }
                } else {
                    deleteArmed.add(player.getUniqueId());
                    player.sendMessage(plugin.prefixedOr("messages.editor-confirm-delete",
                            "<red>Click <white>delete <red>again to confirm."));
                }
            }
            case 53 -> player.closeInventory();
            default -> {
                if (slot >= 28 && slot <= 43) {
                    activatorSlotClick(player, id, cfg, slot - 28, click);
                }
            }
        }
    }

    private void activatorSlotClick(Player player, String id, YamlConfiguration cfg,
            int index, ClickType click) {
        List<String> names = new ArrayList<>(plugin.registry().get(id).activators().keySet());
        names.sort(String::compareTo);
        if (index >= names.size()) {
            return;
        }
        String name = names.get(index);
        if (click.isRightClick()) {
            cfg.set("activators." + name, null);
            refreshItem(player, id);
        } else {
            openActivator(player, id, name);
        }
    }

    // ------------------------------------------------------------ activator clicks

    private void handleActivatorClick(Player player, String id, String activator,
            int slot, ClickType click) {
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) {
            player.closeInventory();
            return;
        }
        String base = "activators." + activator;
        if (!cfg.isConfigurationSection(base)) {
            openItem(player, id);
            return;
        }
        switch (slot) {
            case 10 -> openPicker(player, id, activator);
            case 11 -> askDouble(player, id, "<yellow>Enter cooldown in seconds:",
                    value -> cfg.set(base + ".cooldown-seconds", Math.max(0, value)),
                    () -> openActivator(player, id, activator));
            case 12 -> askDouble(player, id, "<yellow>Enter chance (0.0 - 1.0):",
                    value -> cfg.set(base + ".chance", Math.min(1, Math.max(0, value))),
                    () -> openActivator(player, id, activator));
            case 13 -> askDouble(player, id, "<yellow>Enter mana cost:",
                    value -> cfg.set(base + ".mana-cost", Math.max(0, value)),
                    () -> openActivator(player, id, activator));
            case 14 -> { cfg.set(base + ".cancel-event", !cfg.getBoolean(base + ".cancel-event", false)); refreshActivator(player, id, activator); }
            case 15 -> { cfg.set(base + ".consume-use", !cfg.getBoolean(base + ".consume-use", true)); refreshActivator(player, id, activator); }
            case 16 -> chat.request(player, "<yellow>Enter the message (MiniMessage, empty clears):",
                    answer -> {
                        if (answer == null) { openActivator(player, id, activator); return; }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        live.set(base + ".message", answer.isEmpty() ? null : answer);
                        refreshActivator(player, id, activator);
                    });
            case 26 -> chat.request(player, "<yellow>Enter an action line (e.g. <white>lightning<yellow>):",
                    answer -> {
                        if (answer == null || answer.isEmpty()) { openActivator(player, id, activator); return; }
                        String verb = answer.trim().split("\\s+", 2)[0];
                        if (!ActionExecutor.isKnownVerb(verb)) {
                            player.sendMessage(plugin.prefixedOr("messages.editor-unknown-verb",
                                    "<red>Unknown action verb: <white><verb>", "verb", verb));
                            openActivator(player, id, activator);
                            return;
                        }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        List<String> actions = live.getStringList(base + ".actions");
                        actions.add(answer);
                        live.set(base + ".actions", actions);
                        refreshActivator(player, id, activator);
                    });
            case 35 -> chat.request(player, "<yellow>Enter a console command (<white>%player% <yellow>supported):",
                    answer -> {
                        if (answer == null || answer.isEmpty()) { openActivator(player, id, activator); return; }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        List<String> commands = live.getStringList(base + ".commands");
                        commands.add(answer);
                        live.set(base + ".commands", commands);
                        refreshActivator(player, id, activator);
                    });
            case 44 -> chat.request(player, "<yellow>Enter an effect (<white>type duration amplifier<yellow>):",
                    answer -> {
                        if (answer == null || answer.isEmpty()) { openActivator(player, id, activator); return; }
                        String[] parts = answer.trim().split("\\s+");
                        if (ItemRegistry.effectType(parts[0]) == null) {
                            player.sendMessage(plugin.prefixedOr("messages.editor-unknown-effect",
                                    "<red>Unknown effect: <white><effect>", "effect", parts[0]));
                            openActivator(player, id, activator);
                            return;
                        }
                        YamlConfiguration live = liveConfig(player, id);
                        if (live == null) return;
                        List<String> effects = effectStrings(live, base);
                        effects.add(answer.trim());
                        live.set(base + ".effects", effects);
                        refreshActivator(player, id, activator);
                    });
            case 47 -> openItem(player, id);
            case 49 -> {
                refreshActivator(player, id, activator);
                player.sendMessage(plugin.prefixed("messages.reloaded",
                        "count", String.valueOf(plugin.registry().ids().size())));
            }
            case 53 -> player.closeInventory();
            default -> {
                if (slot >= 19 && slot <= 25) {
                    removeListEntry(player, id, activator, base + ".actions", slot - 19);
                } else if (slot >= 28 && slot <= 34) {
                    removeListEntry(player, id, activator, base + ".commands", slot - 28);
                } else if (slot >= 37 && slot <= 43) {
                    removeEffectEntry(player, id, activator, base, slot - 37);
                }
            }
        }
    }

    private void removeListEntry(Player player, String id, String activator, String path, int index) {
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) return;
        List<String> list = cfg.getStringList(path);
        if (index < list.size()) {
            list.remove(index);
            cfg.set(path, list);
        }
        refreshActivator(player, id, activator);
    }

    private void removeEffectEntry(Player player, String id, String activator, String base, int index) {
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) return;
        List<String> effects = effectStrings(cfg, base);
        if (index < effects.size()) {
            effects.remove(index);
            cfg.set(base + ".effects", effects);
        }
        refreshActivator(player, id, activator);
    }

    // ------------------------------------------------------------ picker clicks

    private void handlePickerClick(Player player, String id, @Nullable String activator, int slot) {
        if (slot == 26) {
            if (activator == null) openItem(player, id);
            else openActivator(player, id, activator);
            return;
        }
        List<Trigger> triggers = new ArrayList<>();
        for (Trigger trigger : Trigger.values()) {
            if (trigger != Trigger.SET_BONUS && trigger != Trigger.LEVEL_UP) {
                triggers.add(trigger);
            }
        }
        if (slot >= triggers.size()) {
            return;
        }
        Trigger picked = triggers.get(slot);
        YamlConfiguration cfg = plugin.registry().rawConfig(id);
        if (cfg == null) {
            player.closeInventory();
            return;
        }
        if (activator == null) {
            int n = 1;
            while (cfg.isConfigurationSection("activators.activator" + n)) n++;
            String name = "activator" + n;
            cfg.set("activators." + name + ".trigger", picked.name());
            cfg.set("activators." + name + ".cooldown-seconds", 0);
            cfg.set("activators." + name + ".actions", new ArrayList<String>());
            refreshActivator(player, id, name);
        } else {
            cfg.set("activators." + activator + ".trigger", picked.name());
            refreshActivator(player, id, activator);
        }
    }

    // ------------------------------------------------------------ helpers

    /** Saves the raw config, reloads everything, and reopens the item screen. */
    private void refreshItem(Player player, String id) {
        if (!plugin.registry().saveItem(id)) {
            player.sendMessage(plugin.prefixedOr("messages.editor-save-failed",
                    "<red>Could not save <white><id><red>.", "id", id));
            player.closeInventory();
            return;
        }
        plugin.reloadItems();
        openItem(player, id);
    }

    /** Saves the raw config, reloads everything, and reopens the activator screen. */
    private void refreshActivator(Player player, String id, String activator) {
        if (!plugin.registry().saveItem(id)) {
            player.sendMessage(plugin.prefixedOr("messages.editor-save-failed",
                    "<red>Could not save <white><id><red>.", "id", id));
            player.closeInventory();
            return;
        }
        plugin.reloadItems();
        openActivator(player, id, activator);
    }

    /** Prompts for a number; invalid input reopens the fallback screen. */
    private void askDouble(Player player, String id, String prompt,
            java.util.function.DoubleConsumer apply, Runnable reopen) {
        chat.request(player, prompt, answer -> {
            if (answer == null) {
                reopen.run();
                return;
            }
            double value;
            try {
                value = Double.parseDouble(answer.trim());
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.prefixedOr("messages.editor-bad-number",
                        "<red>Not a number: <white><input>", "input", answer.trim()));
                reopen.run();
                return;
            }
            YamlConfiguration cfg = plugin.registry().rawConfig(id);
            if (cfg == null) {
                player.closeInventory();
                return;
            }
            apply.accept(value);
            refreshItem(player, id);
        });
    }

    private static String orNone(@Nullable String value) {
        return value == null || value.isEmpty() ? "<gray>(none)" : "<white>" + value;
    }

    private ItemStack button(Material material, String name, List<String> loreLines) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.parse(name));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(TextUtil.parse(line));
            }
            meta.lore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack toggleButton(Material material, String label, boolean on) {
        return button(material, "<yellow>" + label + ": " + (on ? "<green>ON" : "<red>OFF"),
                List.of("<gray>Click to toggle"));
    }

    /** Effects as display strings, accepting both map and shorthand forms. */
    private static List<String> effectStrings(YamlConfiguration cfg, String base) {
        List<String> out = new ArrayList<>();
        for (Object o : cfg.getList(base + ".effects", List.of())) {
            if (o instanceof Map<?, ?> m) {
                Object duration = m.get("duration");
                Object amplifier = m.get("amplifier");
                out.add(m.get("type") + " " + (duration == null ? 200 : duration)
                        + " " + (amplifier == null ? 0 : amplifier));
            } else {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private static final Map<Trigger, String> TRIGGER_DESCRIPTIONS = new LinkedHashMap<>();

    static {
        TRIGGER_DESCRIPTIONS.put(Trigger.RIGHT_CLICK, "Right-click with the item");
        TRIGGER_DESCRIPTIONS.put(Trigger.LEFT_CLICK, "Left-click with the item");
        TRIGGER_DESCRIPTIONS.put(Trigger.SHIFT_RIGHT_CLICK, "Shift + right-click");
        TRIGGER_DESCRIPTIONS.put(Trigger.HIT_ENTITY, "Hit an entity");
        TRIGGER_DESCRIPTIONS.put(Trigger.KILL_ENTITY, "Kill an entity");
        TRIGGER_DESCRIPTIONS.put(Trigger.BLOCK_BREAK, "Break a block");
        TRIGGER_DESCRIPTIONS.put(Trigger.BLOCK_PLACE, "Place a block");
        TRIGGER_DESCRIPTIONS.put(Trigger.TAKE_DAMAGE, "Take damage");
        TRIGGER_DESCRIPTIONS.put(Trigger.PLAYER_DEATH, "Die");
        TRIGGER_DESCRIPTIONS.put(Trigger.EQUIP, "Equip (armor change)");
        TRIGGER_DESCRIPTIONS.put(Trigger.UNEQUIP, "Unequip (armor change)");
        TRIGGER_DESCRIPTIONS.put(Trigger.CONSUME, "Consume the item");
        TRIGGER_DESCRIPTIONS.put(Trigger.ITEM_DROP, "Drop the item");
        TRIGGER_DESCRIPTIONS.put(Trigger.ITEM_PICKUP, "Pick up the item");
        TRIGGER_DESCRIPTIONS.put(Trigger.PROJECTILE_LAUNCH, "Launch a projectile");
        TRIGGER_DESCRIPTIONS.put(Trigger.PROJECTILE_HIT, "Projectile hits something");
        TRIGGER_DESCRIPTIONS.put(Trigger.FISH_CAUGHT, "Catch a fish");
        TRIGGER_DESCRIPTIONS.put(Trigger.SNEAK_TOGGLE, "Toggle sneak");
        TRIGGER_DESCRIPTIONS.put(Trigger.SNEAK_START, "Start sneaking");
        TRIGGER_DESCRIPTIONS.put(Trigger.SPRINT_START, "Start sprinting");
        TRIGGER_DESCRIPTIONS.put(Trigger.GLIDE_START, "Start gliding (elytra)");
        TRIGGER_DESCRIPTIONS.put(Trigger.PLAYER_JOIN, "Join the server");
        TRIGGER_DESCRIPTIONS.put(Trigger.PLAYER_RESPAWN, "Respawn");
        TRIGGER_DESCRIPTIONS.put(Trigger.WORLD_CHANGE, "Change world");
        TRIGGER_DESCRIPTIONS.put(Trigger.LOOP, "Periodic scan while held/worn");
    }
}
