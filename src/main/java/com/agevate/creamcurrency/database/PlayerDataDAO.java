package com.agevate.creamcurrency.database;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.cache.BalanceCache;
import com.agevate.creamcurrency.currency.Currency;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;

/**
 * Optimized Player Data Access Object with caching support.
 */
public class PlayerDataDAO {

    private final CreamCurrency plugin;
    private final BalanceCache cache;
    private final ExecutorService executor;

    // Prepared statement strings (constants for performance)
    private static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS cream_balances (" +
            "player_uuid VARCHAR(36) NOT NULL, " +
            "player_name VARCHAR(32), " +
            "currency_id VARCHAR(32) NOT NULL, " +
            "balance DOUBLE NOT NULL DEFAULT 0, " +
            "PRIMARY KEY (player_uuid, currency_id))";

    // SQL to add player_name column if it doesn't exist (for migration)
    private static final String SQL_ADD_NAME_COLUMN_MYSQL = "ALTER TABLE cream_balances ADD COLUMN IF NOT EXISTS player_name VARCHAR(32)";
    private static final String SQL_ADD_NAME_COLUMN_SQLITE_CHECK = "SELECT COUNT(*) AS cnt FROM pragma_table_info('cream_balances') WHERE name='player_name'";

    private static final String SQL_SELECT_BALANCE = "SELECT balance FROM cream_balances WHERE player_uuid = ? AND currency_id = ?";

    private static final String SQL_UPDATE_ADD = "UPDATE cream_balances SET balance = balance + ? WHERE player_uuid = ? AND currency_id = ?";

    private static final String SQL_UPSERT_BALANCE = "INSERT INTO cream_balances (player_uuid, player_name, currency_id, balance) VALUES (?, ?, ?, ?) "
            +
            "ON DUPLICATE KEY UPDATE balance = VALUES(balance), player_name = VALUES(player_name)";

    private static final String SQL_UPSERT_BALANCE_SQLITE = "INSERT OR REPLACE INTO cream_balances (player_uuid, player_name, currency_id, balance) VALUES (?, ?, ?, ?)";

    // Record for top balance entries that includes player name
    public record TopBalanceEntry(java.util.UUID uuid, String name, double balance) {
    }

    private static final String SQL_CREATE_SETTINGS_TABLE = "CREATE TABLE IF NOT EXISTS cream_player_settings (" +
            "player_uuid VARCHAR(36) PRIMARY KEY, " +
            "payments_disabled BOOLEAN NOT NULL DEFAULT 0)";

    private static final String SQL_SELECT_SETTINGS = "SELECT payments_disabled FROM cream_player_settings WHERE player_uuid = ?";

    private static final String SQL_UPSERT_SETTINGS = "INSERT INTO cream_player_settings (player_uuid, payments_disabled) VALUES (?, ?) "
            +
            "ON DUPLICATE KEY UPDATE payments_disabled = VALUES(payments_disabled)";

    private static final String SQL_UPSERT_SETTINGS_SQLITE = "INSERT OR REPLACE INTO cream_player_settings (player_uuid, payments_disabled) VALUES (?, ?)";

    // Leaderboard cache (short-lived)
    private final java.util.Map<String, CachedTop> topCache = new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedTop(java.util.List<java.util.Map.Entry<java.util.UUID, Double>> list, long timestamp) {
    }

    // Payment toggle cache
    private final java.util.Map<java.util.UUID, Boolean> paymentsDisabledCache = new java.util.concurrent.ConcurrentHashMap<>();

    public PlayerDataDAO(CreamCurrency plugin) {
        this.plugin = plugin;
        this.cache = new BalanceCache();
        // Use a fixed thread pool for database operations
        this.executor = Executors.newFixedThreadPool(
                Math.max(4, Runtime.getRuntime().availableProcessors()),
                r -> {
                    Thread t = new Thread(r, "CreamCurrency-DB-Thread");
                    t.setDaemon(true);
                    return t;
                });
        createTable();
    }

    private void createTable() {
        executor.submit(() -> {
            try (Connection connection = plugin.getDatabase().getConnection()) {
                try (PreparedStatement statement = connection.prepareStatement(SQL_CREATE_TABLE)) {
                    statement.executeUpdate();
                }
                try (PreparedStatement statement = connection.prepareStatement(SQL_CREATE_SETTINGS_TABLE)) {
                    statement.executeUpdate();
                }
                // Migration: Add player_name column if it doesn't exist
                migrateAddPlayerNameColumn(connection);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create database tables!", e);
            }
        });
    }

    private void migrateAddPlayerNameColumn(Connection connection) {
        String dbType = plugin.getConfig().getString("database.type", "SQLITE");
        try {
            if (dbType.equalsIgnoreCase("MYSQL")) {
                try (PreparedStatement stmt = connection.prepareStatement(SQL_ADD_NAME_COLUMN_MYSQL)) {
                    stmt.executeUpdate();
                }
            } else {
                // SQLite: Check if column exists first
                try (PreparedStatement checkStmt = connection.prepareStatement(SQL_ADD_NAME_COLUMN_SQLITE_CHECK);
                        ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && rs.getInt("cnt") == 0) {
                        try (PreparedStatement alterStmt = connection.prepareStatement(
                                "ALTER TABLE cream_balances ADD COLUMN player_name VARCHAR(32)")) {
                            alterStmt.executeUpdate();
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not migrate player_name column (may already exist)", e);
        }
    }

    /**
     * Gets balance from cache first, falls back to database if not cached.
     */
    public CompletableFuture<Double> getBalance(UUID uuid, String currencyId) {
        // Check cache first
        Double cached = cache.get(uuid, currencyId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(SQL_SELECT_BALANCE)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, currencyId);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        double balance = resultSet.getDouble("balance");
                        cache.set(uuid, currencyId, balance);
                        return balance;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get balance for " + uuid, e);
            }

            // Return start balance for new players
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            double startBalance = currency != null ? currency.getStartBalance() : 0.0;
            cache.set(uuid, currencyId, startBalance);
            return startBalance;
        }, executor);
    }

    /**
     * Sets balance and updates cache.
     */
    public CompletableFuture<Void> setBalance(UUID uuid, String currencyId, double amount) {
        return setBalance(uuid, null, currencyId, amount);
    }

    /**
     * Sets balance with player name and updates cache.
     */
    public CompletableFuture<Void> setBalance(UUID uuid, String playerName, String currencyId, double amount) {
        // Update cache immediately for responsiveness
        cache.set(uuid, currencyId, amount);

        // Try to get player name if not provided
        String finalName = playerName;
        if (finalName == null) {
            org.bukkit.OfflinePlayer player = org.bukkit.Bukkit.getOfflinePlayer(uuid);
            if (player.getName() != null) {
                finalName = player.getName();
            }
        }
        final String nameToSave = finalName;

        return CompletableFuture.runAsync(() -> {
            String dbType = plugin.getConfig().getString("database.type", "SQLITE");
            String sql = dbType.equalsIgnoreCase("MYSQL") ? SQL_UPSERT_BALANCE : SQL_UPSERT_BALANCE_SQLITE;

            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, nameToSave);
                statement.setString(3, currencyId);
                statement.setDouble(4, amount);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to set balance for " + uuid, e);
                // Invalidate cache on failure so next read goes to DB
                cache.invalidate(uuid, currencyId);
            }
        }, executor);
    }

    /**
     * Adds amount to current balance (atomic operation in DB).
     */
    public CompletableFuture<Double> addBalance(UUID uuid, String currencyId, double amount) {
        // Optimistic update in cache
        Double current = cache.get(uuid, currencyId);
        if (current != null) {
            cache.set(uuid, currencyId, current + amount);
        }

        // Try to get player name
        org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(uuid);
        final String playerName = offlinePlayer.getName();

        return CompletableFuture.supplyAsync(() -> {
            // Check if player exists in DB first (due to INSERT/UPDATE logic)
            // But we can just use setBalance flow for simplicity IF they don't exist
            // For true atomicity we need to ensure they exist.

            try (Connection connection = plugin.getDatabase().getConnection()) {
                // Try update first
                try (PreparedStatement update = connection.prepareStatement(SQL_UPDATE_ADD)) {
                    update.setDouble(1, amount);
                    update.setString(2, uuid.toString());
                    update.setString(3, currencyId);
                    int rows = update.executeUpdate();

                    if (rows == 0) {
                        // Player doesn't exist, use setBalance logic
                        Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
                        double start = (currency != null ? currency.getStartBalance() : 0.0) + amount;
                        setBalance(uuid, playerName, currencyId, start).join();
                        return start;
                    } else if (playerName != null) {
                        // Update the player name if we have it
                        try (PreparedStatement updateName = connection.prepareStatement(
                                "UPDATE cream_balances SET player_name = ? WHERE player_uuid = ? AND currency_id = ?")) {
                            updateName.setString(1, playerName);
                            updateName.setString(2, uuid.toString());
                            updateName.setString(3, currencyId);
                            updateName.executeUpdate();
                        }
                    }
                }

                // Fetch the new balance to sync cache
                try (PreparedStatement select = connection.prepareStatement(SQL_SELECT_BALANCE)) {
                    select.setString(1, uuid.toString());
                    select.setString(2, currencyId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (rs.next()) {
                            double newBal = rs.getDouble(1);
                            cache.set(uuid, currencyId, newBal);
                            return newBal;
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed atomic add for " + uuid, e);
                cache.invalidate(uuid, currencyId);
            }
            return -1.0;
        }, executor);
    }

    /**
     * Removes amount from current balance if sufficient funds.
     */
    public CompletableFuture<Double> removeBalance(UUID uuid, String currencyId, double amount) {
        return getBalance(uuid, currencyId).thenCompose(current -> {
            if (current < amount) {
                return CompletableFuture.completedFuture(-1.0);
            }
            return addBalance(uuid, currencyId, -amount);
        });
    }

    /**
     * Gets top balances for a currency (with local caching).
     * Defaults offset to 0.
     */
    public CompletableFuture<java.util.List<java.util.Map.Entry<java.util.UUID, Double>>> getTopBalances(
            String currencyId, int limit) {
        return getTopBalances(currencyId, limit, 0);
    }

    /**
     * Gets top balances for a currency (with local caching).
     */
    public CompletableFuture<java.util.List<java.util.Map.Entry<java.util.UUID, Double>>> getTopBalances(
            String currencyId, int limit, int offset) {

        String cacheKey = currencyId + "-" + limit + "-" + offset;
        CachedTop cached = topCache.get(cacheKey);
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 60000) { // 60s cache
            return CompletableFuture.completedFuture(cached.list);
        }

        return CompletableFuture.supplyAsync(() -> {
            java.util.List<java.util.Map.Entry<java.util.UUID, Double>> topList = new java.util.ArrayList<>();
            String sql = "SELECT player_uuid, player_name, balance FROM cream_balances WHERE currency_id = ? ORDER BY balance DESC LIMIT ? OFFSET ?";

            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currencyId);
                statement.setInt(2, limit);
                statement.setInt(3, offset);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        java.util.UUID uuid = java.util.UUID.fromString(resultSet.getString("player_uuid"));
                        double balance = resultSet.getDouble("balance");
                        topList.add(java.util.Map.entry(uuid, balance));
                    }
                }
                topCache.put(cacheKey, new CachedTop(topList, System.currentTimeMillis()));
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get top balances", e);
            }

            return topList;
        }, executor);
    }

    /**
     * Gets top balances for a currency with player names (with local caching).
     * This method returns TopBalanceEntry records that include player names.
     */
    public CompletableFuture<java.util.List<TopBalanceEntry>> getTopBalancesWithNames(
            String currencyId, int limit, int offset) {

        String cacheKey = "named-" + currencyId + "-" + limit + "-" + offset;

        return CompletableFuture.supplyAsync(() -> {
            java.util.List<TopBalanceEntry> topList = new java.util.ArrayList<>();
            String sql = "SELECT player_uuid, player_name, balance FROM cream_balances WHERE currency_id = ? ORDER BY balance DESC LIMIT ? OFFSET ?";

            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currencyId);
                statement.setInt(2, limit);
                statement.setInt(3, offset);

                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        java.util.UUID uuid = java.util.UUID.fromString(resultSet.getString("player_uuid"));
                        String name = resultSet.getString("player_name");
                        double balance = resultSet.getDouble("balance");
                        topList.add(new TopBalanceEntry(uuid, name, balance));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get top balances with names", e);
            }

            return topList;
        }, executor);
    }

    /**
     * Gets the total circulating supply of a currency.
     */
    public CompletableFuture<Double> getTotalBalance(String currencyId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT SUM(balance) FROM cream_balances WHERE currency_id = ?";
            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, currencyId);
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        return rs.getDouble(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get total balance for " + currencyId, e);
            }
            return 0.0;
        }, executor);
    }

    /**
     * Checks if a player has disabled payments.
     */
    public CompletableFuture<Boolean> isPaymentsDisabled(java.util.UUID uuid) {
        Boolean cached = paymentsDisabledCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = plugin.getDatabase().getConnection();
                    PreparedStatement statement = connection.prepareStatement(SQL_SELECT_SETTINGS)) {
                statement.setString(1, uuid.toString());
                try (ResultSet rs = statement.executeQuery()) {
                    if (rs.next()) {
                        boolean disabled = rs.getBoolean("payments_disabled");
                        paymentsDisabledCache.put(uuid, disabled);
                        return disabled;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to get settings for " + uuid, e);
            }
            return false; // Default to enabled
        }, executor);
    }

    /**
     * Toggles payment status for a player.
     */
    public CompletableFuture<Boolean> togglePayments(java.util.UUID uuid) {
        return isPaymentsDisabled(uuid).thenCompose(current -> {
            boolean newValue = !current;
            paymentsDisabledCache.put(uuid, newValue);

            return CompletableFuture.supplyAsync(() -> {
                String dbType = plugin.getConfig().getString("database.type", "SQLITE");
                String sql = dbType.equalsIgnoreCase("MYSQL") ? SQL_UPSERT_SETTINGS : SQL_UPSERT_SETTINGS_SQLITE;

                try (Connection connection = plugin.getDatabase().getConnection();
                        PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, uuid.toString());
                    statement.setBoolean(2, newValue);
                    statement.executeUpdate();
                    return newValue;
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Failed to update settings for " + uuid, e);
                    paymentsDisabledCache.remove(uuid); // Invalidate cache on error
                    return current;
                }
            }, executor);
        });
    }

    public BalanceCache getCache() {
        return cache;
    }

    public void shutdown() {
        executor.shutdown();
    }
}
