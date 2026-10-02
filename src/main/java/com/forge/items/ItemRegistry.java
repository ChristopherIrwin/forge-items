package com.forge.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Consumable;
import io.papermc.paper.datacomponent.item.CustomModelData;
import io.papermc.paper.datacomponent.item.FoodProperties;
import io.papermc.paper.datacomponent.item.ItemAttributeModifiers;
import io.papermc.paper.datacomponent.item.ItemEnchantments;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.UseCooldown;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * Loads item definitions from items/*.yml and builds ItemStacks from them.
 * Item identity is a PDC string tag; usage counts live in a PDC integer.
 */
public final class ItemRegistry {
    private final Plugin plugin;
    private final Logger log;
    private final NamespacedKey idKey;
    private final NamespacedKey usesKey;
    private final Map<String, CustomItem> items = new HashMap<>();
    private final Map<String, SetBonus> setBonuses = new HashMap<>();
    private final Set<NamespacedKey> recipeKeys = new HashSet<>();

    public ItemRegistry(Plugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.idKey = new NamespacedKey(plugin, "id");
        this.usesKey = new NamespacedKey(plugin, "uses_left");
    }

    public NamespacedKey idKey() { return idKey; }
    public NamespacedKey usesKey() { return usesKey; }

    /** (Re)loads every items/*.yml file. Returns the number of items loaded. */
    public int loadAll(java.io.File itemsDir) {
        items.clear();
        if (!itemsDir.exists() && !itemsDir.mkdirs()) {
            log.warning("Could not create items directory: " + itemsDir);
        }
        java.io.File[] files = itemsDir.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return 0;
        }
        int loaded = 0;
        for (java.io.File file : files) {
            try {
                CustomItem item = parse(YamlConfiguration.loadConfiguration(file), file.getName());
                if (item == null) {
                    continue;
                }
                if (items.containsKey(item.id())) {
                    log.warning("Duplicate item id '" + item.id() + "' in " + file.getName() + " — skipped.");
                    continue;
                }
                items.put(item.id(), item);
                loaded++;
            } catch (Exception e) {
                log.warning("Failed to load item file " + file.getName() + ": " + e.getMessage());
            }
        }
        return loaded;
    }

    /** Looks up an item definition by id (case-insensitive); null when unknown or id is null. */
    public @Nullable CustomItem get(@Nullable String id) {
        return id == null ? null : items.get(id.toLowerCase(Locale.ROOT));
    }

    public Set<String> ids() {
        return Set.copyOf(items.keySet());
    }

    /** All loaded set bonuses, keyed by lowercase set id. */
    public Map<String, SetBonus> setBonuses() {
        return Map.copyOf(setBonuses);
    }

    /** (Re)loads sets.yml. Returns the number of sets loaded. */
    public int loadSets(java.io.File file) {
        setBonuses.clear();
        if (!file.exists()) {
            return 0;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = cfg.getConfigurationSection("sets");
        if (sec == null) {
            return 0;
        }
        int loaded = 0;
        for (String setId : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(setId);
            if (s == null) {
                continue;
            }
            List<SetBonus.Tier> tiers = new ArrayList<>();
            for (Object o : s.getList("bonuses", List.of())) {
                if (!(o instanceof Map<?, ?> m)) {
                    continue;
                }
                int pieces = m.get("pieces") instanceof Number n ? n.intValue() : 0;
                if (pieces <= 0) {
                    log.warning("Set '" + setId + "': bonus with invalid pieces — skipped.");
                    continue;
                }
                List<PotionEffect> effects = parseEffectsList(m.get("effects"), "set '" + setId + "'");
                List<String> actions = stringList(m.get("actions"));
                List<String> commands = stringList(m.get("commands"));
                String message = m.get("message") instanceof String str ? str : null;
                tiers.add(new SetBonus.Tier(pieces, effects, actions, commands, message));
            }
            String key = setId.toLowerCase(Locale.ROOT);
            setBonuses.put(key, new SetBonus(key, s.getString("name", setId), tiers));
            loaded++;
        }
        return loaded;
    }

    /** (Re)registers crafting recipes for all loaded items; call after loadAll. */
    public void registerRecipes() {
        for (NamespacedKey key : recipeKeys) {
            Bukkit.removeRecipe(key);
        }
        recipeKeys.clear();
        for (CustomItem def : items.values()) {
            CustomItem.ItemRecipe recipe = def.recipe();
            if (recipe == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + def.id());
            ItemStack result = build(def, 1);
            boolean ok = recipe.shaped()
                    ? registerShaped(key, result, recipe, def.id())
                    : registerShapeless(key, result, recipe, def.id());
            if (ok) {
                recipeKeys.add(key);
            }
        }
    }

    private boolean registerShaped(NamespacedKey key, ItemStack result,
            CustomItem.ItemRecipe recipe, String id) {
        if (recipe.shape().isEmpty() || recipe.shape().size() > 3) {
            log.warning("Item '" + id + "': recipe shape must have 1-3 rows — skipped.");
            return false;
        }
        ShapedRecipe shaped = new ShapedRecipe(key, result);
        try {
            shaped.shape(recipe.shape().toArray(new String[0]));
        } catch (IllegalArgumentException e) {
            log.warning("Item '" + id + "': bad recipe shape — skipped.");
            return false;
        }
        for (Map.Entry<String, String> entry : recipe.shapeIngredients().entrySet()) {
            RecipeChoice choice = choiceFor(entry.getValue(), id);
            if (choice == null) {
                return false;
            }
            shaped.setIngredient(entry.getKey().charAt(0), choice);
        }
        Bukkit.addRecipe(shaped);
        return true;
    }

    private boolean registerShapeless(NamespacedKey key, ItemStack result,
            CustomItem.ItemRecipe recipe, String id) {
        if (recipe.shapelessIngredients().isEmpty()) {
            log.warning("Item '" + id + "': shapeless recipe has no ingredients — skipped.");
            return false;
        }
        ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
        for (String raw : recipe.shapelessIngredients()) {
            RecipeChoice choice = choiceFor(raw, id);
            if (choice == null) {
                return false;
            }
            shapeless.addIngredient(choice);
        }
        Bukkit.addRecipe(shapeless);
        return true;
    }

    /** Resolves an ingredient to a custom item id or a vanilla material. */
    private @Nullable RecipeChoice choiceFor(@Nullable String raw, String itemId) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        CustomItem custom = get(raw);
        if (custom != null) {
            return RecipeChoice.exactChoice(build(custom, 1));
        }
        Material mat = Material.matchMaterial(raw);
        if (mat != null && !mat.isAir()) {
            return RecipeChoice.itemType(mat.asItemType());
        }
        log.warning("Item '" + itemId + "': unknown recipe ingredient '" + raw + "' — recipe skipped.");
        return null;
    }

    /** Returns the ForgeItems id stored on the stack, or null. */
    public @Nullable String getItemId(@Nullable ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        return stack.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    /** Returns the custom-item definition for a stack, or null when it has none. */
    public @Nullable CustomItem getCustomItem(@Nullable ItemStack stack) {
        return get(getItemId(stack));
    }

    /** Builds a fresh ItemStack for the given definition, tagged with its id. */
    public ItemStack build(CustomItem def, int amount) {
        ItemStack stack = new ItemStack(def.material(), Math.max(1, amount));
        if (def.name() != null) {
            stack.setData(DataComponentTypes.CUSTOM_NAME, def.name());
        }
        List<Component> fullLore = new ArrayList<>(def.lore());
        fullLore.add(TextUtil.parse("<gray>Rarity: " + def.rarity().loreLine()));
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(fullLore));
        if (!def.enchantments().isEmpty()) {
            stack.setData(DataComponentTypes.ENCHANTMENTS, ItemEnchantments.itemEnchantments(def.enchantments()));
        }
        if (!def.attributes().isEmpty()) {
            ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.itemAttributes();
            for (Map.Entry<String, CustomItem.AttributeEntry> entry : def.attributes().entrySet()) {
                var attribute = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ATTRIBUTE).get(Key.key(entry.getKey()));
                if (attribute == null) {
                    log.warning("Unknown attribute '" + entry.getKey() + "' on item '" + def.id() + "' — skipped.");
                    continue;
                }
                CustomItem.AttributeEntry ae = entry.getValue();
                var modifier = new AttributeModifier(
                        new NamespacedKey(plugin, def.id() + "_" + entry.getKey().replace(':', '_')),
                        ae.amount(), ae.operation());
                builder.addModifier(attribute, modifier, ae.slotGroup());
            }
            stack.setData(DataComponentTypes.ATTRIBUTE_MODIFIERS, builder);
        }
        if (def.unbreakable()) {
            stack.setData(DataComponentTypes.UNBREAKABLE);
        }
        if (!def.customModelData().isEmpty()) {
            CustomModelData.Builder cmd = CustomModelData.customModelData();
            cmd.addFloats(def.customModelData());
            stack.setData(DataComponentTypes.CUSTOM_MODEL_DATA, cmd);
        }
        if (def.itemModel() != null) {
            stack.setData(DataComponentTypes.ITEM_MODEL, def.itemModel());
        }
        if (def.glintOverride() != null) {
            stack.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, def.glintOverride());
        }
        if (def.maxStackSize() > 0) {
            stack.setData(DataComponentTypes.MAX_STACK_SIZE, def.maxStackSize());
        }
        if (def.globalCooldownSeconds() > 0) {
            stack.setData(DataComponentTypes.USE_COOLDOWN,
                    UseCooldown.useCooldown((float) def.globalCooldownSeconds())
                            .cooldownGroup(Key.key(plugin.getName().toLowerCase(Locale.ROOT), def.id())));
        }
        if (def.consumable()) {
            stack.setData(DataComponentTypes.CONSUMABLE,
                    Consumable.consumable().consumeSeconds(def.consumeSeconds()));
        }
        if (def.foodNutrition() > 0) {
            stack.setData(DataComponentTypes.FOOD,
                    FoodProperties.food().nutrition(def.foodNutrition()).saturation(def.foodSaturation()));
        }
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, def.id());
        if (def.usageLimit() > 0) {
            meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, def.usageLimit());
        }
        stack.setItemMeta(meta);
        return stack;
    }

    // ------------------------------------------------------------------ parsing

    /** Parses one item file; returns null when the file is invalid (caller logs). */
    private @Nullable CustomItem parse(YamlConfiguration cfg, String fileName) {
        String id = cfg.getString("id", "").trim().toLowerCase(Locale.ROOT);
        if (id.isEmpty()) {
            log.warning("Item file " + fileName + " has no id — skipped.");
            return null;
        }
        Material material = Material.matchMaterial(cfg.getString("material", ""));
        if (material == null || material.isAir()) {
            log.warning("Item '" + id + "' has unknown material '" + cfg.getString("material") + "' — skipped.");
            return null;
        }
        Component name = cfg.isString("name") ? TextUtil.parse(cfg.getString("name")) : null;
        List<Component> lore = new ArrayList<>();
        for (String line : cfg.getStringList("lore")) {
            lore.add(TextUtil.parse(line));
        }

        Map<Enchantment, Integer> enchantments = new HashMap<>();
        ConfigurationSection enchSec = cfg.getConfigurationSection("enchantments");
        if (enchSec != null) {
            for (String key : enchSec.getKeys(false)) {
                Key enchKey = key.contains(":") ? Key.key(key) : Key.key("minecraft", key);
                Enchantment ench = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT).get(enchKey);
                if (ench == null) {
                    log.warning("Item '" + id + "': unknown enchantment '" + key + "' — skipped.");
                    continue;
                }
                enchantments.put(ench, Math.max(1, enchSec.getInt(key, 1)));
            }
        }

        Map<String, CustomItem.AttributeEntry> attributes = new HashMap<>();
        ConfigurationSection attrSec = cfg.getConfigurationSection("attributes");
        if (attrSec != null) {
            for (String key : attrSec.getKeys(false)) {
                ConfigurationSection a = attrSec.getConfigurationSection(key);
                if (a == null) {
                    continue;
                }
                AttributeModifier.Operation op;
                try {
                    op = AttributeModifier.Operation.valueOf(a.getString("operation", "ADD_NUMBER").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    log.warning("Item '" + id + "': unknown attribute operation '" + a.getString("operation") + "' — skipped.");
                    continue;
                }
                EquipmentSlotGroup group = parseSlotGroup(a.getString("slot", "ANY"), id);
                String attrKey = key.contains(":") ? key : "minecraft:" + key;
                attributes.put(attrKey, new CustomItem.AttributeEntry(a.getDouble("amount", 0), op, group));
            }
        }

        List<Float> cmd = new ArrayList<>();
        if (cfg.isList("custom-model-data")) {
            for (Object o : cfg.getList("custom-model-data", List.of())) {
                if (o instanceof Number n) {
                    cmd.add(n.floatValue());
                }
            }
        } else if (cfg.isDouble("custom-model-data") || cfg.isInt("custom-model-data")) {
            cmd.add((float) cfg.getDouble("custom-model-data"));
        }

        Key itemModel = null;
        if (cfg.isString("item-model")) {
            try {
                itemModel = Key.key(cfg.getString("item-model"));
            } catch (IllegalArgumentException e) {
                log.warning("Item '" + id + "': invalid item-model key '" + cfg.getString("item-model") + "' — skipped.");
            }
        }

        Boolean glint = cfg.isBoolean("glint-override") ? cfg.getBoolean("glint-override") : null;
        boolean consumable = cfg.getBoolean("consumable", false);
        float consumeSeconds = (float) cfg.getDouble("consume-seconds", 1.6);

        Map<String, Activator> activators = new HashMap<>();
        ConfigurationSection actSec = cfg.getConfigurationSection("activators");
        if (actSec != null) {
            for (String actName : actSec.getKeys(false)) {
                ConfigurationSection a = actSec.getConfigurationSection(actName);
                if (a == null) {
                    continue;
                }
                Trigger trigger = Trigger.parse(a.getString("trigger"));
                if (trigger == null) {
                    log.warning("Item '" + id + "': activator '" + actName + "' has unknown trigger '"
                            + a.getString("trigger") + "' — skipped.");
                    continue;
                }
                Boolean sneaking = null;
                String permission = null;
                double minHealth = 0;
                double maxHealth = Double.MAX_VALUE;
                Set<String> biomes = new HashSet<>();
                Activator.TimeMode timeMode = Activator.TimeMode.ANY;
                Activator.WeatherMode weather = Activator.WeatherMode.ANY;
                int minLight = 0;
                int maxLight = 15;
                Set<String> targetTypes = new HashSet<>();
                ConfigurationSection cond = a.getConfigurationSection("conditions");
                if (cond != null) {
                    if (cond.isBoolean("sneaking")) sneaking = cond.getBoolean("sneaking");
                    if (cond.isString("permission")) permission = cond.getString("permission");
                    minHealth = cond.getDouble("min-health", 0);
                    maxHealth = cond.getDouble("max-health", Double.MAX_VALUE);
                    for (String biome : cond.getStringList("biomes")) {
                        biomes.add(biome.toLowerCase(Locale.ROOT));
                    }
                    timeMode = parseTimeMode(cond.getString("time"), id, actName);
                    weather = parseWeatherMode(cond.getString("weather"), id, actName);
                    minLight = Math.max(0, Math.min(15, cond.getInt("min-light", 0)));
                    maxLight = Math.max(0, Math.min(15, cond.getInt("max-light", 15)));
                    for (String type : cond.getStringList("target-types")) {
                        targetTypes.add(type.toUpperCase(Locale.ROOT));
                    }
                }
                List<PotionEffect> effects = parseEffectsList(
                        a.getList("effects", List.of()),
                        "Item '" + id + "' activator '" + actName + "'");
                Component message = a.isString("message") ? TextUtil.parse(a.getString("message")) : null;
                String messageRaw = a.isString("message") ? a.getString("message") : null;
                activators.put(actName.toLowerCase(Locale.ROOT), new Activator(
                        actName,
                        trigger,
                        a.getDouble("cooldown-seconds", 0),
                        a.getDouble("chance", 1.0),
                        a.getBoolean("cancel-event", false),
                        a.getBoolean("consume-use", trigger != Trigger.LOOP),
                        sneaking,
                        permission,
                        minHealth,
                        maxHealth,
                        biomes,
                        timeMode,
                        weather,
                        minLight,
                        maxLight,
                        targetTypes,
                        a.getStringList("actions"),
                        a.getStringList("commands"),
                        message,
                        messageRaw,
                        effects));
            }
        }

        Set<String> worlds = new HashSet<>();
        for (String w : cfg.getStringList("restricted-worlds")) {
            worlds.add(w.toLowerCase(Locale.ROOT));
        }

        Rarity rarity = Rarity.COMMON;
        if (cfg.isString("rarity")) {
            Rarity parsed = Rarity.fromString(cfg.getString("rarity"));
            if (parsed == null) {
                log.warning("Item '" + id + "': unknown rarity '" + cfg.getString("rarity")
                        + "' — using COMMON.");
            } else {
                rarity = parsed;
            }
        }
        String setId = null;
        if (cfg.isString("set")) {
            String raw = cfg.getString("set", "").trim().toLowerCase(Locale.ROOT);
            if (!raw.isEmpty()) {
                setId = raw;
            }
        }
        CustomItem.ItemRecipe recipe = parseRecipe(cfg.getConfigurationSection("recipe"), id);

        return new CustomItem(
                id, material, name, lore, enchantments, attributes,
                cfg.getBoolean("unbreakable", false), cmd, itemModel, glint,
                cfg.getInt("max-stack-size", 0), cfg.getBoolean("keep-on-death", false),
                cfg.getInt("usage-limit", 0), cfg.getDouble("cooldown-seconds", 0),
                worlds, cfg.isString("use-permission") ? cfg.getString("use-permission") : null,
                consumable, consumeSeconds,
                cfg.getInt("food.nutrition", 0), (float) cfg.getDouble("food.saturation", 0),
                rarity, setId, recipe,
                activators);
    }

    /** Parses an effects list (map or shorthand-string entries); warns on unknown types. */
    private List<PotionEffect> parseEffectsList(@Nullable Object raw, String where) {
        List<PotionEffect> effects = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return effects;
        }
        for (Object o : list) {
            if (o instanceof Map<?, ?> m) {
                PotionEffectType type = effectType(String.valueOf(m.get("type")));
                if (type == null) {
                    log.warning(where + " has unknown effect '" + m.get("type") + "' — skipped.");
                    continue;
                }
                int dur = m.get("duration") instanceof Number n ? n.intValue() : 200;
                int amp = m.get("amplifier") instanceof Number n ? n.intValue() : 0;
                effects.add(new PotionEffect(type, Math.max(1, dur), Math.max(0, amp)));
            } else if (o instanceof String s) {
                // shorthand: "minecraft:speed 200 1"
                String[] parts = s.split("\\s+");
                PotionEffectType type = effectType(parts[0]);
                if (type == null) {
                    log.warning(where + " has unknown effect '" + parts[0] + "' — skipped.");
                    continue;
                }
                int dur = parts.length > 1 ? parseInt(parts[1], 200) : 200;
                int amp = parts.length > 2 ? parseInt(parts[2], 0) : 0;
                effects.add(new PotionEffect(type, Math.max(1, dur), Math.max(0, amp)));
            }
        }
        return effects;
    }

    private static List<String> stringList(@Nullable Object raw) {
        List<String> out = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                out.add(String.valueOf(o));
            }
        }
        return out;
    }

    private Activator.TimeMode parseTimeMode(@Nullable String raw, String id, String actName) {
        if (raw == null) {
            return Activator.TimeMode.ANY;
        }
        try {
            return Activator.TimeMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Item '" + id + "': activator '" + actName + "' has unknown time '"
                    + raw + "' — ignored.");
            return Activator.TimeMode.ANY;
        }
    }

    private Activator.WeatherMode parseWeatherMode(@Nullable String raw, String id, String actName) {
        if (raw == null) {
            return Activator.WeatherMode.ANY;
        }
        try {
            return Activator.WeatherMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warning("Item '" + id + "': activator '" + actName + "' has unknown weather '"
                    + raw + "' — ignored.");
            return Activator.WeatherMode.ANY;
        }
    }

    /** Parses the optional recipe section; null when absent or invalid (caller logs). */
    private @Nullable CustomItem.ItemRecipe parseRecipe(
            @Nullable ConfigurationSection rSec, String id) {
        if (rSec == null) {
            return null;
        }
        boolean shaped = !"shapeless".equalsIgnoreCase(rSec.getString("type", "shaped"));
        if (shaped) {
            List<String> shape = rSec.getStringList("shape");
            ConfigurationSection ingSec = rSec.getConfigurationSection("ingredients");
            Map<String, String> shapeIngredients = new HashMap<>();
            if (ingSec != null) {
                for (String key : ingSec.getKeys(false)) {
                    if (key.length() == 1) {
                        shapeIngredients.put(key, String.valueOf(ingSec.get(key)));
                    }
                }
            }
            if (shape.isEmpty() || shapeIngredients.isEmpty()) {
                log.warning("Item '" + id + "': recipe needs a shape and ingredients — skipped.");
                return null;
            }
            return new CustomItem.ItemRecipe(true, shape, shapeIngredients, List.of());
        }
        List<String> flat = new ArrayList<>();
        ConfigurationSection ingSec = rSec.getConfigurationSection("ingredients");
        if (ingSec != null) {
            for (String key : ingSec.getKeys(false)) {
                flat.add(String.valueOf(ingSec.get(key)));
            }
        }
        flat.addAll(rSec.getStringList("ingredients"));
        if (flat.isEmpty()) {
            log.warning("Item '" + id + "': shapeless recipe has no ingredients — skipped.");
            return null;
        }
        return new CustomItem.ItemRecipe(false, List.of(), Map.of(), flat);
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Maps a config slot-group name to the static instance (not a Java enum). */
    private EquipmentSlotGroup parseSlotGroup(String raw, String itemId) {
        EquipmentSlotGroup group = switch (raw.toUpperCase(Locale.ROOT)) {
            case "MAINHAND" -> EquipmentSlotGroup.MAINHAND;
            case "OFFHAND" -> EquipmentSlotGroup.OFFHAND;
            case "HAND" -> EquipmentSlotGroup.HAND;
            case "ARMOR" -> EquipmentSlotGroup.ARMOR;
            case "HEAD" -> EquipmentSlotGroup.HEAD;
            case "CHEST" -> EquipmentSlotGroup.CHEST;
            case "LEGS" -> EquipmentSlotGroup.LEGS;
            case "FEET" -> EquipmentSlotGroup.FEET;
            case "BODY" -> EquipmentSlotGroup.BODY;
            case "ANY" -> EquipmentSlotGroup.ANY;
            default -> null;
        };
        if (group == null) {
            log.warning("Item '" + itemId + "': unknown equipment slot group '" + raw + "' — using ANY.");
            return EquipmentSlotGroup.ANY;
        }
        return group;
    }

    /** Modern (non-deprecated) potion-effect lookup; null for unknown effect keys. */
    static @Nullable PotionEffectType effectType(String raw) {
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)
                .get(Key.key(raw.contains(":") ? raw : "minecraft:" + raw));
    }
}
