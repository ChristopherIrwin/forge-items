package com.forge.items;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Single-question chat prompts. While a session is open, the player's next
 * chat message is captured (and hidden from chat) instead of broadcast.
 * The callback receives the plain-text message, or null on cancel/timeout/quit.
 */
public final class ChatInput implements Listener {
    /** Sessions expire after two minutes without an answer. */
    private static final long TIMEOUT_MS = 120_000;

    private record Session(Consumer<@Nullable String> callback, long expiresAt) {}

    private final ForgeItems plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public ChatInput(ForgeItems plugin) {
        this.plugin = plugin;
    }

    /** Starts the timeout sweeper. */
    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 100L, 100L);
    }

    /**
     * Prompts the player; their next chat message goes to the callback.
     * Typing "cancel" aborts. Any open inventory is closed first.
     */
    public void request(Player player, String prompt, Consumer<@Nullable String> callback) {
        player.closeInventory();
        sessions.put(player.getUniqueId(),
                new Session(callback, System.currentTimeMillis() + TIMEOUT_MS));
        player.sendMessage(TextUtil.parse(prompt));
        player.sendMessage(TextUtil.parse("<gray>Type <red>cancel <gray>to abort."));
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        var expired = sessions.entrySet().stream()
                .filter(e -> e.getValue().expiresAt() < now)
                .map(Map.Entry::getKey)
                .toList();
        for (UUID uuid : expired) {
            Session session = sessions.remove(uuid);
            if (session != null) {
                session.callback().accept(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        String answer = text.equalsIgnoreCase("cancel") ? null : text;
        Bukkit.getScheduler().runTask(plugin, () -> session.callback().accept(answer));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.remove(event.getPlayer().getUniqueId());
        if (session != null) {
            session.callback().accept(null);
        }
    }
}
