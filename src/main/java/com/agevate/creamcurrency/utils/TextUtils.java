package com.agevate.creamcurrency.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Modern text utilities using Adventure API (Paper native).
 * Supports both legacy color codes and MiniMessage format.
 */
public final class TextUtils {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    private TextUtils() {
    }

    /**
     * Parses a string with legacy color codes (&amp;) to a Component.
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return LEGACY_SERIALIZER.deserialize(text);
    }

    /**
     * Parses a MiniMessage formatted string to a Component.
     */
    public static Component miniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Converts a Component back to a legacy string.
     */
    public static String toLegacy(Component component) {
        return LEGACY_SERIALIZER.serialize(component);
    }

    /**
     * Sends an actionbar message to a player.
     */
    public static void sendActionBar(Player player, String message) {
        if (player == null || !player.isOnline())
            return;
        player.sendActionBar(colorize(message));
    }

    /**
     * Sends a title to a player.
     */
    public static void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        if (player == null || !player.isOnline())
            return;
        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeIn * 50L),
                Duration.ofMillis(stay * 50L),
                Duration.ofMillis(fadeOut * 50L));
        player.showTitle(Title.title(colorize(title), colorize(subtitle), times));
    }

    /**
     * Plays a sound to a player from a string format: "SOUND_NAME, volume, pitch"
     */
    public static void playSound(Player player, String soundPath) {
        if (player == null || !player.isOnline() || soundPath == null || soundPath.isEmpty()) {
            return;
        }

        try {
            String[] parts = soundPath.split(",");
            String soundName = parts[0].trim().toUpperCase();
            float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0f;
            float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0f;

            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception e) {
            org.bukkit.Bukkit.getLogger()
                    .warning("[CreamCurrency] Failed to play sound: '" + soundPath + "'. Error: " + e.getMessage());
        }
    }
}
