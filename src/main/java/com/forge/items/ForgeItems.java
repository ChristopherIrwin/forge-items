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
    private SetBonusManager setBonuses;
    private ItemBrowserGui browser;
    private int itemCount;
    private int setCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new ItemRegistry(this);
        cooldowns = new CooldownManager();
        actions = new ActionExecutor(getLogger());
        listener = new ItemListener(this, registry, cooldowns, actions);
        setBonuses = new SetBonusManager(this, registry, actions, cooldowns);
        browser = new ItemBrowserGui(this);

        reloadItems();

        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(setBonuses, this);
        getServer().getPluginManager().registerEvents(browser, this);
        var command = new ForgeItemsCommand(this);
        var cmd = getCommand("forgeitems");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        listener.startLoopTask();
        setBonuses.start();

        getLogger().info("ForgeItems 2.0.0 enabled with " + itemCount + " item(s) and "
                + setCount + " set(s).");
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
            saveResource("items/ember_blade.yml", false);
            saveResource("items/ember_chestplate.yml", false);
        }
        File setsFile = new File(getDataFolder(), "sets.yml");
        if (!setsFile.exists()) {
            saveResource("sets.yml", false);
        }
        itemCount = registry.loadAll(dir);
        setCount = registry.loadSets(setsFile);
        registry.registerRecipes();
        // Validate every action line so typos surface at load, not mid-fight.
        for (String id : registry.ids()) {
            CustomItem item = registry.get(id);
            for (Activator act : item.activators().values()) {
                actions.validate(item.id(), act.name(), act.actions());
            }
        }
        for (SetBonus bonus : registry.setBonuses().values()) {
            for (SetBonus.Tier tier : bonus.tiers()) {
                actions.validate("set:" + bonus.id(), tier.pieces() + "pc", tier.actions());
            }
        }
        return itemCount;
    }

    public ItemRegistry registry() { return registry; }
    public int itemCount() { return itemCount; }
    public int setCount() { return setCount; }
    public ItemBrowserGui browser() { return browser; }

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
