package com.forge.items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** /forgeitems reload | give <id> [player] [amount] | list */
public final class ForgeItemsCommand implements CommandExecutor, TabCompleter {
    private final ForgeItems plugin;

    public ForgeItemsCommand(ForgeItems plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(TextUtil.parse("<gray>Usage: <white>/forgeitems <reload|give|list>"));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            case "give" -> give(sender, args);
            default -> sender.sendMessage(TextUtil.parse("<gray>Usage: <white>/forgeitems <reload|give|list>"));
        }
        return true;
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("forgeitems.admin")) {
            sender.sendMessage(plugin.prefixed("messages.no-permission"));
            return;
        }
        int count = plugin.reloadItems();
        sender.sendMessage(plugin.prefixed("messages.reloaded", "count", String.valueOf(count)));
    }

    private void list(CommandSender sender) {
        if (!sender.hasPermission("forgeitems.list")) {
            sender.sendMessage(plugin.prefixed("messages.no-permission"));
            return;
        }
        var ids = new ArrayList<>(plugin.registry().ids());
        ids.sort(String::compareTo);
        sender.sendMessage(plugin.prefixed("messages.item-list-header", "count", String.valueOf(ids.size())));
        for (String id : ids) {
            CustomItem item = plugin.registry().get(id);
            sender.sendMessage(plugin.prefixed("messages.item-list-entry",
                    "id", id, "name", plugin.plainName(item)));
        }
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission("forgeitems.give")) {
            sender.sendMessage(plugin.prefixed("messages.no-permission"));
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(TextUtil.parse("<gray>Usage: <white>/forgeitems give <id> [player] [amount]"));
            return;
        }
        CustomItem item = plugin.registry().get(args[1]);
        if (item == null) {
            sender.sendMessage(plugin.prefixed("messages.unknown-item", "id", args[1]));
            return;
        }
        Player target;
        int amount = 1;
        if (args.length >= 3) {
            target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                // args[2] might be the amount when no player is given
                try {
                    amount = Integer.parseInt(args[2]);
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(TextUtil.parse("<red>Specify a player when running from console."));
                        return;
                    }
                    target = player;
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.parse("<red>Player not found: <white>" + args[2]));
                    return;
                }
            } else if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(TextUtil.parse("<red>Invalid amount: <white>" + args[3]));
                    return;
                }
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(TextUtil.parse("<red>Specify a player when running from console."));
            return;
        }
        amount = Math.max(1, Math.min(64, amount));
        ItemStack stack = plugin.registry().build(item, amount);
        var leftover = target.getInventory().addItem(stack);
        for (ItemStack rest : leftover.values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), rest);
        }
        String name = plugin.plainName(item);
        sender.sendMessage(plugin.prefixed("messages.item-given",
                "amount", String.valueOf(amount), "name", name, "player", target.getName()));
        if (!sender.equals(target)) {
            target.sendMessage(plugin.prefixed("messages.item-received",
                    "amount", String.valueOf(amount), "name", name));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reload", "give", "list"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(new ArrayList<>(plugin.registry().ids()), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            return filter(names, args[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> options, String prefix) {
        String low = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(low)) {
                out.add(o);
            }
        }
        return out;
    }
}
