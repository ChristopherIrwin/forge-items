package com.forge.items;

import java.util.List;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.Nullable;

/**
 * One parsed activator entry from an item's YAML definition.
 * Immutable after construction.
 */
public final class Activator {
    /** Day/night gate for the {@code time} condition. */
    public enum TimeMode { ANY, DAY, NIGHT }
    /** Weather gate for the {@code weather} condition. */
    public enum WeatherMode { ANY, CLEAR, RAIN, THUNDER }

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
    private final Set<String> biomes;
    private final TimeMode timeMode;
    private final WeatherMode weather;
    private final int minLight;
    private final int maxLight;
    private final Set<String> targetTypes;
    private final List<String> actions;
    private final List<String> commands;
    private final Component message;
    private final String messageRaw;
    private final List<PotionEffect> effects;

    @SuppressWarnings("java:S107") // many fields are inherent to an activator definition
    public Activator(String name, Trigger trigger, double cooldownSeconds, double chance,
            boolean cancelEvent, boolean consumeUse, @Nullable Boolean sneaking,
            @Nullable String permission, double minHealth, double maxHealth,
            Set<String> biomes, TimeMode timeMode, WeatherMode weather,
            int minLight, int maxLight, Set<String> targetTypes,
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
        this.biomes = Set.copyOf(biomes);
        this.timeMode = timeMode;
        this.weather = weather;
        this.minLight = minLight;
        this.maxLight = maxLight;
        this.targetTypes = Set.copyOf(targetTypes);
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
    /** Allowed biomes (lowercase keys); empty means any biome. */
    public Set<String> biomes() { return biomes; }
    public TimeMode timeMode() { return timeMode; }
    public WeatherMode weather() { return weather; }
    public int minLight() { return minLight; }
    public int maxLight() { return maxLight; }
    /** Allowed target entity types (uppercase names); empty means any target. */
    public Set<String> targetTypes() { return targetTypes; }
    public List<String> actions() { return actions; }
    public List<String> commands() { return commands; }
    /** Parsed message, or null when the activator YAML defines none. */
    public @Nullable Component message() { return message; }
    /** Raw message with placeholders, or null when the activator YAML defines none. */
    public @Nullable String messageRaw() { return messageRaw; }
    public List<PotionEffect> effects() { return effects; }
}
