package com.agevate.creamcurrency;

import com.agevate.creamcurrency.config.CommandConfig;
import com.agevate.creamcurrency.currency.CurrencyManager;
import com.agevate.creamcurrency.database.Database;
import com.agevate.creamcurrency.database.MySQLDatabase;
import com.agevate.creamcurrency.database.PlayerDataDAO;
import com.agevate.creamcurrency.database.SQLiteDatabase;
import com.agevate.creamcurrency.hook.VaultHook;
import com.agevate.creamcurrency.logging.TransactionLogger;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class CreamCurrency extends JavaPlugin {

    private static CreamCurrency instance;
    private CurrencyManager currencyManager;
    private CommandConfig commandConfig;
    private Database database;
    private PlayerDataDAO playerDataDAO;
    private VaultHook vaultHook;
    private TransactionLogger transactionLogger;

    @Override
    public void onEnable() {
        instance = this;

        // Load configurations
        saveDefaultConfig();
        commandConfig = new CommandConfig(this);

        // Load Currencies
        currencyManager = new CurrencyManager(this);
        currencyManager.loadCurrencies();

        // Initialize Database
        setupDatabase();
        playerDataDAO = new PlayerDataDAO(this);

        // Initialize Transaction Logger
        transactionLogger = new TransactionLogger(this);

        // Hook into Vault
        if (getServer().getPluginManager().getPlugin("Vault") != null) {
            vaultHook = new VaultHook(this);
            getServer().getServicesManager().register(net.milkbowl.vault.economy.Economy.class, vaultHook, this,
                    ServicePriority.Highest);
            getLogger().info("Vault hooked successfully!");
        } else {
            getLogger().warning("Vault not found! Vault support disabled.");
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new com.agevate.creamcurrency.hook.CreamPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked successfully!");
        } else {
            getLogger().warning("PlaceholderAPI not found!");
        }

        // Register Commands
        var moneyCmd = new com.agevate.creamcurrency.command.MoneyCommand(this);
        getCommand("money").setExecutor(moneyCmd);
        getCommand("money").setTabCompleter(moneyCmd);

        var currencyCmd = new com.agevate.creamcurrency.command.CurrencyCommand(this);
        getCommand("currency").setExecutor(currencyCmd);
        getCommand("currency").setTabCompleter(currencyCmd);

        var adminCmd = new com.agevate.creamcurrency.command.CreamCurrencyCommand(this);
        getCommand("creamcurrency").setExecutor(adminCmd);
        getCommand("creamcurrency").setTabCompleter(adminCmd);

        // Register Listeners
        getServer().getPluginManager().registerEvents(new com.agevate.creamcurrency.utils.PlayerListener(this), this);

        // Register dynamic commands for currency aliases
        registerDynamicCommands();

        getLogger().info("CreamCurrency enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Shutdown transaction logger first
        if (transactionLogger != null) {
            transactionLogger.shutdown();
        }

        // Shutdown DAO executor
        if (playerDataDAO != null) {
            playerDataDAO.shutdown();
        }

        // Then close database connections
        if (database != null) {
            database.close();
        }

        getLogger().info("CreamCurrency disabled.");
    }

    private void setupDatabase() {
        String type = getConfig().getString("database.type", "SQLITE");
        if (type.equalsIgnoreCase("MYSQL")) {
            database = new MySQLDatabase(this);
        } else {
            database = new SQLiteDatabase(this);
        }

        try {
            database.getConnection().close(); // Test connection
            getLogger().info("Database connected successfully (" + type + ")");
        } catch (Exception e) {
            getLogger().severe("Failed to connect to database!");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    public static CreamCurrency getInstance() {
        return instance;
    }

    public CurrencyManager getCurrencyManager() {
        return currencyManager;
    }

    public Database getDatabase() {
        return database;
    }

    public PlayerDataDAO getPlayerDataDAO() {
        return playerDataDAO;
    }

    public CommandConfig getCommandConfig() {
        return commandConfig;
    }

    public TransactionLogger getTransactionLogger() {
        return transactionLogger;
    }

    /**
     * Registers dynamic commands for each currency based on their ID and aliases.
     * Uses Bukkit's CommandMap to register commands at runtime.
     */
    private void registerDynamicCommands() {
        try {
            var commandMapField = getServer().getClass().getDeclaredField("commandMap");
            commandMapField.setAccessible(true);
            var commandMap = (org.bukkit.command.CommandMap) commandMapField.get(getServer());

            var dynamicHandler = new com.agevate.creamcurrency.command.DynamicCurrencyCommand(this);

            for (var currency : currencyManager.getCurrencies().values()) {
                // Register currency ID as command
                registerDynamicCommand(commandMap, currency.getId(), dynamicHandler);

                // Register all aliases
                for (String alias : currency.getAliases()) {
                    registerDynamicCommand(commandMap, alias, dynamicHandler);
                }
            }

            getLogger().info("Dynamic currency commands registered.");
        } catch (Exception e) {
            getLogger().warning("Could not register dynamic commands: " + e.getMessage());
        }
    }

    private void registerDynamicCommand(org.bukkit.command.CommandMap commandMap, String name,
            org.bukkit.command.CommandExecutor executor) {
        // Skip if command already exists
        if (commandMap.getCommand(name) != null) {
            return;
        }

        var cmd = new org.bukkit.command.defaults.BukkitCommand(name) {
            @Override
            public boolean execute(org.bukkit.command.CommandSender sender, String label, String[] args) {
                return executor.onCommand(sender, this, label, args);
            }

            @Override
            public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias,
                    String[] args) {
                if (executor instanceof org.bukkit.command.TabCompleter tabCompleter) {
                    return tabCompleter.onTabComplete(sender, this, alias, args);
                }
                return null;
            }
        };
        cmd.setDescription("Currency command for " + name);
        cmd.setPermission("creamcurrency.use");

        commandMap.register("creamcurrency", cmd);
        getLogger().info("Registered command: /" + name);
    }
}
