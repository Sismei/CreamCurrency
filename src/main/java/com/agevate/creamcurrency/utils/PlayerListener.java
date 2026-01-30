package com.agevate.creamcurrency.utils;

import com.agevate.creamcurrency.CreamCurrency;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Handles player-related events for cache management and optimization.
 */
public class PlayerListener implements Listener {

    private final CreamCurrency plugin;

    public PlayerListener(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        // Pre-load primary currency balance asynchronously
        var primary = plugin.getCurrencyManager().getPrimaryCurrency();
        if (primary != null) {
            plugin.getPlayerDataDAO().getBalance(event.getPlayer().getUniqueId(), primary.getId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        // Clear player cache to prevent memory leaks
        plugin.getPlayerDataDAO().getCache().invalidatePlayer(event.getPlayer().getUniqueId());
    }
}
