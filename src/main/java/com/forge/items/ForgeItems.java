package com.forge.items;

import java.io.File;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

/** ForgeItems — YAML-defined custom items with activators, actions and cooldowns. */
public final class ForgeItems extends JavaPlugin {
    private ItemRegistry registry;
    private CooldownManager cooldowns;
    private ActionExecutor actions;
    private ItemListener listener;
    private int itemCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new ItemRegistry(this);
        cooldowns = new CooldownManager();
        actions = new ActionExecutor(getLogger());
        listener = new ItemListener(this, registry, cooldowns, actions);

        reloadItems();

        getServer().getPluginManager().registerEvents(listener, this);
        var command = new ForgeItemsCommand(this);
        var cmd = getCommand("forgeitems");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        listener.startLoopTask();

        getLogger().info("ForgeItems 1.0.0 enabled with " + itemCount + " item(s).");
    }

    @Override
    public void onDisable() {
        getLogger().info("ForgeItems disabled.");
    }

    /** (Re)loads config + all item definitions. Returns the item count. */
    public int reloadItems() {
        reloadConfig();
        File dir = new File(getDataFolder(), "items");
        if (!dir.exists()) {
            dir.mkdirs();
            saveResource("items/thunder_hammer.yml", false);
            saveResource("items/frost_bow.yml", false);
            saveResource("items/guardian_chestplate.yml", false);
        }
        itemCount = registry.loadAll(dir);
        // Validate every action line so typos surface at load, not mid-fight.
        for (String id : registry.ids()) {
            CustomItem item = registry.get(id);
            for (Activator act : item.activators().values()) {
                actions.validate(item.id(), act.name(), act.actions());
            }
        }
        return itemCount;
    }

    public ItemRegistry registry() { return registry; }
    public int itemCount() { return itemCount; }

    /** Plain-text version of an item's display name (for messages). */
    public String plainName(CustomItem item) {
        if (item.name() == null) {
            return item.id();
        }
        return PlainTextComponentSerializer.plainText().serialize(item.name());
    }

    /** A configured message with prefix and %placeholder% replacement. */
    public Component prefixed(String key, String... pairs) {
        String prefix = getConfig().getString("settings.message-prefix", "");
        String raw = getConfig().getString(key, key);
        String resolved = raw;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolved = resolved.replace("<" + pairs[i] + ">", pairs[i + 1]);
        }
        return TextUtil.parse(prefix + resolved);
    }
}
