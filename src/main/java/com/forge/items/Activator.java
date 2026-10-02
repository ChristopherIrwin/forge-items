package com.forge.items;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * One parsed activator entry from an item's YAML definition.
 * Immutable after construction.
 */
public final class Activator {
    private final String name;
    private final Trigger trigger;
    private final double cooldownSeconds;
    private final double chance;
    private final boolean cancelEvent;
    private final boolean consumeUse;
    private final Boolean sneaking;
    private final String permission;
    private final double minHealth;
    private final double maxHealth;
    private final List<String> actions;
    private final List<String> commands;
    private final Component message;
    private final String messageRaw;
    private final List<PotionEffect> effects;

    public Activator(String name, Trigger trigger, double cooldownSeconds, double chance,
            boolean cancelEvent, boolean consumeUse, @Nullable Boolean sneaking,
            @Nullable String permission, double minHealth, double maxHealth,
            List<String> actions, List<String> commands, @Nullable Component message,
            @Nullable String messageRaw, List<PotionEffect> effects) {
        this.name = name;
        this.trigger = trigger;
        this.cooldownSeconds = cooldownSeconds;
        this.chance = chance;
        this.cancelEvent = cancelEvent;
        this.consumeUse = consumeUse;
        this.sneaking = sneaking;
        this.permission = permission;
        this.minHealth = minHealth;
        this.maxHealth = maxHealth;
        this.actions = List.copyOf(actions);
        this.commands = List.copyOf(commands);
        this.message = message;
        this.messageRaw = messageRaw;
        this.effects = List.copyOf(effects);
    }

    public String name() { return name; }
    public Trigger trigger() { return trigger; }
    public double cooldownSeconds() { return cooldownSeconds; }
    public double chance() { return chance; }
    public boolean cancelEvent() { return cancelEvent; }
    public boolean consumeUse() { return consumeUse; }
    /** Required sneak state, or null when the activator YAML defines none. */
    public @Nullable Boolean sneaking() { return sneaking; }
    /** Required permission, or null when the activator YAML defines none. */
    public @Nullable String permission() { return permission; }
    public double minHealth() { return minHealth; }
    public double maxHealth() { return maxHealth; }
    public List<String> actions() { return actions; }
    public List<String> commands() { return commands; }
    /** Parsed message, or null when the activator YAML defines none. */
    public @Nullable Component message() { return message; }
    /** Raw message with placeholders, or null when the activator YAML defines none. */
    public @Nullable String messageRaw() { return messageRaw; }
    public List<PotionEffect> effects() { return effects; }
}
