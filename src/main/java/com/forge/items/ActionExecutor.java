package com.forge.items;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.Locale;
import java.util.logging.Logger;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Executes the built-in action language plus console commands.
 * Action grammar (space-separated, first token is the verb):
 *   damage &lt;amount&gt; | heal &lt;amount&gt; | launch [power] | lightning |
 *   explode &lt;power&gt; | potion &lt;effect&gt; &lt;ticks&gt; &lt;amplifier&gt; |
 *   message &lt;MiniMessage&gt; | broadcast &lt;MiniMessage&gt; |
 *   command &lt;console command&gt; | cooldown &lt;seconds&gt; |
 *   sound &lt;key&gt; [volume] [pitch] |
 *   particle &lt;key&gt; [count] [spread] [speed] |
 *   title &lt;MiniMessage&gt; | subtitle &lt;MiniMessage&gt; | actionbar &lt;MiniMessage&gt; |
 *   particle &lt;key&gt; [count] [spread] [speed] |
 *   title &lt;MiniMessage&gt; | subtitle &lt;MiniMessage&gt; | actionbar &lt;MiniMessage&gt; |
 *   feed [amount] | extinguish | sudo &lt;player command&gt; |
 *   spawnmob &lt;type&gt; [count] | mana &lt;amount&gt; | giveitem &lt;id&gt; [amount] |
 *   ignite [ticks] | freeze [ticks] | clearpotion [effect] | fly &lt;on|off|toggle&gt;
 * Unknown verbs are logged once as warnings at item-load time (see validate()).
 */
public final class ActionExecutor {
    private final ForgeItems plugin;
    private final Logger log;

    public ActionExecutor(ForgeItems plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /** Checks action lines for unknown verbs; returns true if all are known. */
    public boolean validate(String itemId, String activatorName, java.util.List<String> actions) {
        boolean ok = true;
        for (String line : actions) {
            String verb = line.trim().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
            switch (verb) {
                case "damage", "heal", "launch", "lightning", "explode", "potion",
                     "message", "broadcast", "command", "cooldown", "sound",
                     "particle", "title", "subtitle", "actionbar", "feed",
                     "extinguish", "sudo", "spawnmob", "mana", "giveitem",
                     "ignite", "freeze", "clearpotion", "fly" -> { /* known */ }
                default -> {
                    log.warning("Item '" + itemId + "' activator '" + activatorName
                            + "': unknown action '" + verb + "' — it will be skipped at runtime.");
                    ok = false;
                }
            }
        }
        return ok;
    }

    public void execute(ActivationContext ctx) {
        Activator activator = ctx.activator();
        for (String line : activator.actions()) {
            runAction(ctx, line);
        }
        for (String cmd : activator.commands()) {
            String resolved = ctx.resolve(cmd);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
        if (activator.messageRaw() != null) {
            ctx.player().sendMessage(TextUtil.parse(ctx.resolve(activator.messageRaw())));
        }
        for (PotionEffect effect : activator.effects()) {
            ctx.player().addPotionEffect(effect);
        }
    }

    private void runAction(ActivationContext ctx, String line) {
        String[] parts = line.trim().split("\\s+", 2);
        String verb = parts[0].toLowerCase(Locale.ROOT);
        String rest = parts.length > 1 ? parts[1] : "";
        Player player = ctx.player();
        try {
            switch (verb) {
                case "damage" -> {
                    double amount = parseDouble(rest, 1);
                    Entity target = ctx.target() instanceof LivingEntity le ? le : player;
                    if (target instanceof LivingEntity le2) {
                        le2.damage(amount, player);
                    }
                }
                case "heal" -> {
                    double amount = parseDouble(rest, 2);
                    double max = maxHealth(player);
                    player.setHealth(Math.min(max, player.getHealth() + amount));
                }
                case "launch" -> {
                    double power = rest.isEmpty() ? 1.5 : parseDouble(rest.split("\\s+")[0], 1.5);
                    player.setVelocity(player.getLocation().getDirection().multiply(power));
                }
                case "lightning" -> player.getWorld().strikeLightning(ctx.effectLocation());
                case "explode" -> {
                    float power = (float) parseDouble(rest.isEmpty() ? "2" : rest.split("\\s+")[0], 2);
                    player.getWorld().createExplosion(ctx.effectLocation(), power);
                }
                case "potion" -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 1 || args[0].isEmpty()) break;
                    PotionEffectType type = ItemRegistry.effectType(args[0]);
                    if (type == null) {
                        log.warning("Unknown potion effect '" + args[0] + "' in action '" + line + "'.");
                        break;
                    }
                    int duration = args.length > 1 ? parseInt(args[1], 200) : 200;
                    int amp = args.length > 2 ? parseInt(args[2], 0) : 0;
                    LivingEntity target = ctx.target() instanceof LivingEntity le ? le : player;
                    target.addPotionEffect(new PotionEffect(type, Math.max(1, duration), Math.max(0, amp)));
                }
                case "message" -> player.sendMessage(TextUtil.parse(ctx.resolve(rest)));
                case "broadcast" -> Bukkit.broadcast(TextUtil.parse(ctx.resolve(rest)));
                case "command" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ctx.resolve(rest));
                case "cooldown" -> {
                    double seconds = parseDouble(rest.isEmpty() ? "1" : rest.split("\\s+")[0], 1);
                    player.setCooldown(ctx.stack().getType(), (int) Math.round(seconds * 20));
                }
                case "sound" -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 1 || args[0].isEmpty()) break;
                    float volume = args.length > 1 ? (float) parseDouble(args[1], 1.0) : 1.0f;
                    float pitch = args.length > 2 ? (float) parseDouble(args[2], 1.0) : 1.0f;
                    player.playSound(Sound.sound(Key.key(args[0]),
                            net.kyori.adventure.sound.Sound.Source.PLAYER, volume, pitch));
                }
                case "particle" -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 1 || args[0].isEmpty()) break;
                    Particle particle;
                    try {
                        String key = args[0].contains(":")
                                ? args[0].substring(args[0].indexOf(':') + 1) : args[0];
                        particle = Particle.valueOf(key.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        log.warning("Unknown particle '" + args[0] + "' in action '" + line + "'.");
                        break;
                    }
                    int count = args.length > 1 ? parseInt(args[1], 20) : 20;
                    double spread = args.length > 2 ? parseDouble(args[2], 0.5) : 0.5;
                    double speed = args.length > 3 ? parseDouble(args[3], 0.1) : 0.1;
                    player.getWorld().spawnParticle(particle, ctx.effectLocation(),
                            Math.max(1, count), spread, spread, spread, speed);
                }
                case "title" -> player.showTitle(
                        Title.title(TextUtil.parse(ctx.resolve(rest)), Component.empty()));
                case "subtitle" -> player.showTitle(
                        Title.title(Component.empty(), TextUtil.parse(ctx.resolve(rest))));
                case "actionbar" -> player.sendActionBar(TextUtil.parse(ctx.resolve(rest)));
                case "feed" -> {
                    int amount = rest.isEmpty() ? 20 : parseInt(rest.split("\\s+")[0], 20);
                    player.setFoodLevel(Math.min(20, player.getFoodLevel() + Math.max(0, amount)));
                    player.setSaturation(Math.min(20.0f, player.getSaturation() + 5.0f));
                }
                case "extinguish" -> player.setFireTicks(0);
                case "sudo" -> {
                    if (rest.isEmpty()) break;
                    player.performCommand(ctx.resolve(rest));
                }
                case "spawnmob" -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 1 || args[0].isEmpty()) break;
                    EntityType type;
                    try {
                        type = EntityType.valueOf(args[0].toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        log.warning("Unknown entity type '" + args[0] + "' in action '" + line + "'.");
                        break;
                    }
                    int count = args.length > 1 ? parseInt(args[1], 1) : 1;
                    count = Math.max(1, Math.min(10, count));
                    for (int i = 0; i < count; i++) {
                        player.getWorld().spawnEntity(ctx.effectLocation(), type);
                    }
                }
                case "mana" -> {
                    double amount = rest.isEmpty() ? 0 : parseDouble(rest.split("\\s+")[0], 0);
                    ManaManager mana = plugin.mana();
                    if (amount >= 0) {
                        mana.give(player, amount);
                    } else {
                        mana.take(player, -amount);
                    }
                }
                case "giveitem" -> {
                    String[] args = rest.split("\\s+");
                    if (args.length < 1 || args[0].isEmpty()) break;
                    CustomItem def = plugin.registry().get(args[0]);
                    if (def == null) {
                        log.warning("Unknown item '" + args[0] + "' in action '" + line + "'.");
                        break;
                    }
                    int amount = args.length > 1 ? parseInt(args[1], 1) : 1;
                    amount = Math.max(1, Math.min(64, amount));
                    var leftover = player.getInventory().addItem(plugin.registry().build(def, amount));
                    for (var drop : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                case "ignite" -> {
                    int ticks = rest.isEmpty() ? 100 : parseInt(rest.split("\\s+")[0], 100);
                    Entity target = ctx.target() != null ? ctx.target() : player;
                    target.setFireTicks(Math.max(0, ticks));
                }
                case "freeze" -> {
                    int ticks = rest.isEmpty() ? 100 : parseInt(rest.split("\\s+")[0], 100);
                    LivingEntity target = ctx.target() instanceof LivingEntity le ? le : player;
                    target.setFreezeTicks(Math.max(0, ticks));
                }
                case "clearpotion" -> {
                    if (rest.isEmpty()) {
                        for (PotionEffect active : player.getActivePotionEffects()) {
                            player.removePotionEffect(active.getType());
                        }
                    } else {
                        PotionEffectType type = ItemRegistry.effectType(rest.split("\\s+")[0]);
                        if (type != null) {
                            player.removePotionEffect(type);
                        }
                    }
                }
                case "fly" -> {
                    String mode = rest.isEmpty() ? "toggle" : rest.split("\\s+")[0].toLowerCase(Locale.ROOT);
                    switch (mode) {
                        case "on" -> {
                            player.setAllowFlight(true);
                            player.setFlying(true);
                        }
                        case "off" -> {
                            player.setFlying(false);
                            player.setAllowFlight(false);
                        }
                        default -> { // toggle
                            if (player.isFlying()) {
                                player.setFlying(false);
                                player.setAllowFlight(false);
                            } else {
                                player.setAllowFlight(true);
                                player.setFlying(true);
                            }
                        }
                    }
                }
                default -> { /* validated at load; ignore silently */ }
            }
        } catch (IllegalArgumentException e) {
            log.warning("Bad action arguments in '" + line + "': " + e.getMessage());
        }
    }

    private double maxHealth(Player player) {
        AttributeInstance inst = player.getAttribute(RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.ATTRIBUTE).getOrThrow(Key.key("minecraft:max_health")));
        return inst != null ? inst.getValue() : 20.0;
    }

    private static double parseDouble(String s, double fallback) {
        try {
            return Double.parseDouble(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int parseInt(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
