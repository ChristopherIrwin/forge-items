package com.forge.items;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.jetbrains.annotations.Nullable;

/** One parsed custom-item definition. Immutable after construction. */
public final class CustomItem {
    /** A single attribute modifier entry from config. */
    public record AttributeEntry(double amount, AttributeModifier.Operation operation,
            EquipmentSlotGroup slotGroup) {}

    /** A crafting recipe that produces this item. */
    public record ItemRecipe(boolean shaped, List<String> shape,
            Map<String, String> shapeIngredients, List<String> shapelessIngredients) {
        public ItemRecipe {
            shape = List.copyOf(shape);
            shapeIngredients = Map.copyOf(shapeIngredients);
            shapelessIngredients = List.copyOf(shapelessIngredients);
        }
    }

    /** Level/XP configuration; null when the item does not level. */
    public record ItemLevels(int maxLevel, double xpBase, double xpGrowth,
            int xpPerTrigger, int xpPerKill, int xpPerBlockBreak,
            @Nullable String levelUpMessage, List<String> levelUpActions,
            List<String> levelUpCommands) {
        public ItemLevels {
            levelUpActions = List.copyOf(levelUpActions);
            levelUpCommands = List.copyOf(levelUpCommands);
        }
    }

    private final String id;
    private final Material material;
    private final Component name;
    private final List<Component> lore;
    private final Map<Enchantment, Integer> enchantments;
    private final Map<String, AttributeEntry> attributes;
    private final boolean unbreakable;
    private final List<Float> customModelData;
    private final Key itemModel;
    private final Boolean glintOverride;
    private final int maxStackSize;
    private final boolean keepOnDeath;
    private final int usageLimit;
    private final double globalCooldownSeconds;
    private final Set<String> restrictedWorlds;
    private final String usePermission;
    private final boolean consumable;
    private final float consumeSeconds;
    private final int foodNutrition;
    private final float foodSaturation;
    private final Rarity rarity;
    private final String setId;
    private final ItemRecipe recipe;
    private final ItemLevels levels;
    private final Map<String, Activator> activators;

    @SuppressWarnings("java:S107") // many fields are inherent to an item definition
    public CustomItem(String id, Material material, @Nullable Component name, List<Component> lore,
            Map<Enchantment, Integer> enchantments, Map<String, AttributeEntry> attributes,
            boolean unbreakable, List<Float> customModelData, @Nullable Key itemModel,
            @Nullable Boolean glintOverride, int maxStackSize, boolean keepOnDeath, int usageLimit,
            double globalCooldownSeconds, Set<String> restrictedWorlds,
            @Nullable String usePermission, boolean consumable, float consumeSeconds,
            int foodNutrition, float foodSaturation, Rarity rarity,
            @Nullable String setId, @Nullable ItemRecipe recipe,
            @Nullable ItemLevels levels,
            Map<String, Activator> activators) {
        this.id = id;
        this.material = material;
        this.name = name;
        this.lore = List.copyOf(lore);
        this.enchantments = Map.copyOf(enchantments);
        this.attributes = Map.copyOf(attributes);
        this.unbreakable = unbreakable;
        this.customModelData = List.copyOf(customModelData);
        this.itemModel = itemModel;
        this.glintOverride = glintOverride;
        this.maxStackSize = maxStackSize;
        this.keepOnDeath = keepOnDeath;
        this.usageLimit = usageLimit;
        this.globalCooldownSeconds = globalCooldownSeconds;
        this.restrictedWorlds = Set.copyOf(restrictedWorlds);
        this.usePermission = usePermission;
        this.consumable = consumable;
        this.consumeSeconds = consumeSeconds;
        this.foodNutrition = foodNutrition;
        this.foodSaturation = foodSaturation;
        this.rarity = rarity;
        this.setId = setId;
        this.recipe = recipe;
        this.levels = levels;
        this.activators = Map.copyOf(activators);
    }

    public String id() { return id; }
    public Material material() { return material; }
    /** Display name, or null when the item YAML defines none. */
    public @Nullable Component name() { return name; }
    public List<Component> lore() { return lore; }
    public Map<Enchantment, Integer> enchantments() { return enchantments; }
    public Map<String, AttributeEntry> attributes() { return attributes; }
    public boolean unbreakable() { return unbreakable; }
    public List<Float> customModelData() { return customModelData; }
    /** Item model key, or null when the item YAML defines none. */
    public @Nullable Key itemModel() { return itemModel; }
    /** Glint override, or null when the item YAML defines none. */
    public @Nullable Boolean glintOverride() { return glintOverride; }
    public int maxStackSize() { return maxStackSize; }
    public boolean keepOnDeath() { return keepOnDeath; }
    public int usageLimit() { return usageLimit; }
    public double globalCooldownSeconds() { return globalCooldownSeconds; }
    public Set<String> restrictedWorlds() { return restrictedWorlds; }
    /** Use-gate permission, or null when the item YAML defines none. */
    public @Nullable String usePermission() { return usePermission; }
    public boolean consumable() { return consumable; }
    public float consumeSeconds() { return consumeSeconds; }
    public int foodNutrition() { return foodNutrition; }
    public float foodSaturation() { return foodSaturation; }
    public Rarity rarity() { return rarity; }
    /** Item-set id, or null when the item belongs to no set. */
    public @Nullable String setId() { return setId; }
    /** Crafting recipe, or null when the item has none. */
    public @Nullable ItemRecipe recipe() { return recipe; }
    /** Level/XP config, or null when the item does not level. */
    public @Nullable ItemLevels levels() { return levels; }
    public Map<String, Activator> activators() { return activators; }

    /**
     * Permission a player needs to *use* this item, or null when the item
     * defines no gate. Only set when the item YAML configures one explicitly —
     * unconfigured items are usable by everyone.
     */
    public @Nullable String effectiveUsePermission() {
        return usePermission;
    }
}
