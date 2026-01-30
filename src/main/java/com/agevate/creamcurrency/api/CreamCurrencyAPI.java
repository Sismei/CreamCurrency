package com.agevate.creamcurrency.api;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.currency.Currency;
import org.bukkit.OfflinePlayer;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for CreamCurrency plugin.
 * Provides methods to interact with the economy system.
 */
public class CreamCurrencyAPI {

    /**
     * getting the plugin instance.
     */
    private static CreamCurrency getPlugin() {
        return CreamCurrency.getInstance();
    }

    /**
     * Checks if a currency with the given ID exists.
     *
     * @param currencyId The ID of the currency.
     * @return true if exists, false otherwise.
     */
    public static boolean hasCurrency(String currencyId) {
        return getPlugin().getCurrencyManager().getCurrency(currencyId) != null;
    }

    /**
     * Gets a currency object by its ID.
     *
     * @param currencyId The ID of the currency.
     * @return The Currency object, or null if not found.
     */
    public static Currency getCurrency(String currencyId) {
        return getPlugin().getCurrencyManager().getCurrency(currencyId);
    }

    /**
     * Gets all loaded currencies.
     *
     * @return A collection of all currencies.
     */
    public static Collection<Currency> getCurrencies() {
        return getPlugin().getCurrencyManager().getCurrencies().values();
    }

    /**
     * Gets the primary currency of the server.
     *
     * @return The primary Currency object.
     */
    public static Currency getPrimaryCurrency() {
        return getPlugin().getCurrencyManager().getPrimaryCurrency();
    }

    /**
     * Formats an amount for a specific currency.
     *
     * @param currencyId The ID of the currency.
     * @param amount     The amount to format.
     * @return The formatted string (e.g., "100 Gold"), or just the number if
     *         currency not found.
     */
    public static String format(String currencyId, double amount) {
        Currency currency = getCurrency(currencyId);
        if (currency != null) {
            return currency.format(amount);
        }
        return String.valueOf(amount);
    }

    /**
     * Gets the balance of a player asynchronously.
     *
     * @param playerUuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return A CompletableFuture containing the balance.
     */
    public static CompletableFuture<Double> getBalance(UUID playerUuid, String currencyId) {
        return getPlugin().getPlayerDataDAO().getBalance(playerUuid, currencyId);
    }

    /**
     * Gets the balance of a player asynchronously.
     *
     * @param player     The player.
     * @param currencyId The ID of the currency.
     * @return A CompletableFuture containing the balance.
     */
    public static CompletableFuture<Double> getBalance(OfflinePlayer player, String currencyId) {
        return getBalance(player.getUniqueId(), currencyId);
    }

    /**
     * Sets the balance of a player asynchronously.
     *
     * @param playerUuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount     The new balance.
     * @return A CompletableFuture that completes when the operation is done.
     */
    public static CompletableFuture<Void> setBalance(UUID playerUuid, String currencyId, double amount) {
        return getPlugin().getPlayerDataDAO().setBalance(playerUuid, currencyId, amount);
    }

    /**
     * Sets the balance of a player asynchronously.
     *
     * @param player     The player.
     * @param currencyId The ID of the currency.
     * @param amount     The new balance.
     * @return A CompletableFuture that completes when the operation is done.
     */
    public static CompletableFuture<Void> setBalance(OfflinePlayer player, String currencyId, double amount) {
        return setBalance(player.getUniqueId(), currencyId, amount);
    }

    /**
     * Adds money to a player's balance asynchronously.
     *
     * @param playerUuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to add.
     * @return A CompletableFuture containing the new balance.
     */
    public static CompletableFuture<Double> addBalance(UUID playerUuid, String currencyId, double amount) {
        return getPlugin().getPlayerDataDAO().addBalance(playerUuid, currencyId, amount);
    }

    /**
     * Adds money to a player's balance asynchronously.
     *
     * @param player     The player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to add.
     * @return A CompletableFuture containing the new balance.
     */
    public static CompletableFuture<Double> addBalance(OfflinePlayer player, String currencyId, double amount) {
        return addBalance(player.getUniqueId(), currencyId, amount);
    }

    /**
     * Removes money from a player's balance asynchronously.
     *
     * @param playerUuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to remove.
     * @return A CompletableFuture containing the new balance, or -1.0 if
     *         insufficient funds.
     */
    public static CompletableFuture<Double> removeBalance(UUID playerUuid, String currencyId, double amount) {
        return getPlugin().getPlayerDataDAO().removeBalance(playerUuid, currencyId, amount);
    }

    /**
     * Removes money from a player's balance asynchronously.
     *
     * @param player     The player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to remove.
     * @return A CompletableFuture containing the new balance, or -1.0 if
     *         insufficient funds.
     */
    public static CompletableFuture<Double> removeBalance(OfflinePlayer player, String currencyId, double amount) {
        return removeBalance(player.getUniqueId(), currencyId, amount);
    }

    /**
     * Checks if a player has enough balance asynchronously.
     *
     * @param playerUuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to check.
     * @return A CompletableFuture containing true if the player has enough, false
     *         otherwise.
     */
    public static CompletableFuture<Boolean> hasEnough(UUID playerUuid, String currencyId, double amount) {
        return getBalance(playerUuid, currencyId).thenApply(balance -> balance >= amount);
    }

    /**
     * Checks if a player has enough balance asynchronously.
     *
     * @param player     The player.
     * @param currencyId The ID of the currency.
     * @param amount     The amount to check.
     * @return A CompletableFuture containing true if the player has enough, false
     *         otherwise.
     */
    public static CompletableFuture<Boolean> hasEnough(OfflinePlayer player, String currencyId, double amount) {
        return hasEnough(player.getUniqueId(), currencyId, amount);
    }
}
