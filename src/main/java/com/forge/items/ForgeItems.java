package com.forge.items;

import java.io.File;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.plugin.java.JavaPlugin;

/** ForgeItems — YAML-defined custom items with activators, actions and cooldowns. */
public final class ForgeItems extends JavaPlugin {
    private ItemRegistry registry;
    private CooldownManager cooldowns;
    private ManaManager mana;
    private ActionExecutor actions;
    private ItemListener listener;
    private SetBonusManager setBonuses;
    private ItemLevelManager levels;
    private DropManager drops;
    private ItemBrowserGui browser;
    private ChatInput chatInput;
    private ItemEditorGui editor;
    private int itemCount;
    private int setCount;
    private int dropCount;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new ItemRegistry(this);
        cooldowns = new CooldownManager();
        mana = new ManaManager(this);
        actions = new ActionExecutor(this);
        levels = new ItemLevelManager(this, actions);
        listener = new ItemListener(this, registry, cooldowns, actions);
        setBonuses = new SetBonusManager(this, registry, actions, cooldowns);
        drops = new DropManager(registry);
        browser = new ItemBrowserGui(this);
        chatInput = new ChatInput(this);
        editor = new ItemEditorGui(this, chatInput);

        reloadItems();

        var pm = getServer().getPluginManager();
        pm.registerEvents(listener, this);
        pm.registerEvents(setBonuses, this);
        pm.registerEvents(drops, this);
        pm.registerEvents(browser, this);
        pm.registerEvents(chatInput, this);
        pm.registerEvents(editor, this);
        var command = new ForgeItemsCommand(this);
        var cmd = getCommand("forgeitems");
        if (cmd != null) {
            cmd.setExecutor(command);
            cmd.setTabCompleter(command);
        }
        listener.startLoopTask();
        setBonuses.start();
        mana.start();
        chatInput.start();

        getLogger().info("ForgeItems 4.0.0 enabled with " + itemCount + " item(s), "
                + setCount + " set(s) and " + dropCount + " mob drop(s).");
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
        File dropsFile = new File(getDataFolder(), "drops.yml");
        if (!dropsFile.exists()) {
            saveResource("drops.yml", false);
        }
        itemCount = registry.loadAll(dir);
        setCount = registry.loadSets(setsFile);
        List<DropManager.MobDrop> mobDrops = registry.loadDrops(dropsFile);
        drops.setDrops(mobDrops);
        dropCount = mobDrops.size();
        registry.registerRecipes();
        // Validate every action line so typos surface at load, not mid-fight.
        for (String id : registry.ids()) {
            CustomItem item = registry.get(id);
            for (Activator act : item.activators().values()) {
                actions.validate(item.id(), act.name(), act.actions());
            }
            if (item.levels() != null) {
                actions.validate(item.id(), "level-up", item.levels().levelUpActions());
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
    public ManaManager mana() { return mana; }
    public ItemLevelManager levels() { return levels; }
    public int itemCount() { return itemCount; }
    public int setCount() { return setCount; }
    public int dropCount() { return dropCount; }
    public ItemBrowserGui browser() { return browser; }
    public ItemEditorGui editor() { return editor; }

    /** Plain-text version of an item's display name (for messages). */
    public String plainName(CustomItem item) {
        if (item.name() == null) {
            return item.id();
        }
        return PlainTextComponentSerializer.plainText().serialize(item.name());
    }

    /** A configured message with prefix and <placeholder> replacement, with a fallback. */
    public Component prefixedOr(String key, String fallback, String... pairs) {
        String prefix = getConfig().getString("settings.message-prefix", "");
        String raw = getConfig().getString(key, fallback);
        String resolved = raw;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            resolved = resolved.replace("<" + pairs[i] + ">", pairs[i + 1]);
        }
        return TextUtil.parse(prefix + resolved);
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
