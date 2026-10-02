package com.forge.items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Item XP and level-ups. XP and level live in the stack's PDC; the lore
 * carries a "Level: N" line; level-ups fire a message plus actions/commands.
 */
public final class ItemLevelManager {
    private final ForgeItems plugin;
    private final ActionExecutor actions;
    private final NamespacedKey xpKey;
    private final NamespacedKey levelKey;

    public ItemLevelManager(ForgeItems plugin, ActionExecutor actions) {
        this.plugin = plugin;
        this.actions = actions;
        this.xpKey = new NamespacedKey(plugin, "xp");
        this.levelKey = new NamespacedKey(plugin, "level");
    }

    /** XP needed to go from {@code level} to {@code level + 1}. */
    public static double xpFor(int level, CustomItem.ItemLevels levels) {
        return levels.xpBase() * Math.pow(levels.xpGrowth(), level - 1);
    }

    /**
     * Awards XP to a stack; handles level-ups (lore, message, actions).
     * The slot is used to write the stack back, like durability consumption;
     * pass -1 to skip the write-back (and the award).
     */
    public void awardXp(Player player, int slot, ItemStack stack, CustomItem def, int amount) {
        CustomItem.ItemLevels levels = def.levels();
        if (levels == null || amount <= 0 || slot < 0) {
            return;
        }
        var meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        var pdc = meta.getPersistentDataContainer();
        int xp = pdc.getOrDefault(xpKey, PersistentDataType.INTEGER, 0);
        int level = pdc.getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
        if (level >= levels.maxLevel()) {
            return;
        }
        xp += amount;
        boolean leveled = false;
        while (level < levels.maxLevel() && xp >= xpFor(level, levels)) {
            xp -= (int) xpFor(level, levels);
            level++;
            leveled = true;
        }
        pdc.set(xpKey, PersistentDataType.INTEGER, xp);
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        stack.setItemMeta(meta);
        player.getInventory().setItem(slot, stack);
        stack.setData(DataComponentTypes.LORE, ItemLore.lore(ItemRegistry.loreFor(def, level)));
        if (leveled) {
            onLevelUp(player, stack, def, level, levels);
        }
    }

    /** Current level of a stack (1 when never leveled). */
    public int levelOf(ItemStack stack) {
        var meta = stack.getItemMeta();
        if (meta == null) {
            return 1;
        }
        return meta.getPersistentDataContainer()
                .getOrDefault(levelKey, PersistentDataType.INTEGER, 1);
    }

    private void onLevelUp(Player player, ItemStack stack, CustomItem def,
            int level, CustomItem.ItemLevels levels) {
        String name = plugin.plainName(def);
        String levelStr = String.valueOf(level);
        if (levels.levelUpMessage() != null) {
            player.sendMessage(TextUtil.parse(levels.levelUpMessage()
                    .replace("%level%", levelStr)
                    .replace("%name%", name)));
        }
        List<String> actions = new ArrayList<>();
        for (String action : levels.levelUpActions()) {
            actions.add(action.replace("%level%", levelStr).replace("%name%", name));
        }
        List<String> commands = new ArrayList<>();
        for (String command : levels.levelUpCommands()) {
            commands.add(command.replace("%level%", levelStr).replace("%name%", name));
        }
        if (!actions.isEmpty() || !commands.isEmpty()) {
            Activator synthetic = Activator.synthetic(
                    "level-up", Trigger.LEVEL_UP, actions, commands);
            this.actions.execute(new ActivationContext(player, def, synthetic, stack, null, null));
        }
        player.playSound(Sound.sound(Key.key("minecraft:entity.player.levelup"),
                Sound.Source.PLAYER, 1.0f, 1.0f));
    }
}
