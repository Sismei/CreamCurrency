package com.agevate.creamcurrency.logging;

import com.agevate.creamcurrency.CreamCurrency;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles transaction logging to file.
 * All logging is done asynchronously to prevent performance impact.
 */
public class TransactionLogger {

    private final CreamCurrency plugin;
    private final Path logDirectory;
    private final ExecutorService executor;
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final boolean enabled;

    public TransactionLogger(CreamCurrency plugin) {
        this.plugin = plugin;
        this.logDirectory = plugin.getDataFolder().toPath().resolve("logs");
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "CreamCurrency-Logger");
            t.setDaemon(true);
            return t;
        });
        this.enabled = plugin.getConfig().getBoolean("logging.enabled", true);

        // Create logs directory
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not create logs directory: " + e.getMessage());
        }
    }

    /**
     * Log a payment between players
     */
    public void logPayment(UUID sender, String senderName, UUID receiver, String receiverName,
            String currencyId, double amount) {
        if (!enabled)
            return;

        String message = String.format("[PAY] %s (%s) -> %s (%s) | Currency: %s | Amount: %.2f",
                senderName, sender.toString(),
                receiverName, receiver.toString(),
                currencyId, amount);
        log("pay", message);
    }

    /**
     * Log an admin give command
     */
    public void logAdminGive(String adminName, UUID target, String targetName,
            String currencyId, double amount, double newBalance) {
        if (!enabled)
            return;

        String message = String.format("[ADMIN-GIVE] %s gave %.2f %s to %s (%s) | New Balance: %.2f",
                adminName, amount, currencyId, targetName, target.toString(), newBalance);
        log("admin", message);
    }

    /**
     * Log an admin set command
     */
    public void logAdminSet(String adminName, UUID target, String targetName,
            String currencyId, double oldBalance, double newBalance) {
        if (!enabled)
            return;

        String message = String.format("[ADMIN-SET] %s set %s's (%s) %s balance from %.2f to %.2f",
                adminName, targetName, target.toString(), currencyId, oldBalance, newBalance);
        log("admin", message);
    }

    /**
     * Log an admin remove command
     */
    public void logAdminRemove(String adminName, UUID target, String targetName,
            String currencyId, double amount, double newBalance) {
        if (!enabled)
            return;

        String message = String.format("[ADMIN-REMOVE] %s removed %.2f %s from %s (%s) | New Balance: %.2f",
                adminName, amount, currencyId, targetName, target.toString(), newBalance);
        log("admin", message);
    }

    /**
     * Log a Vault transaction
     */
    public void logVaultTransaction(String type, UUID player, String playerName, double amount, double newBalance) {
        if (!enabled)
            return;

        String message = String.format("[VAULT-%s] %s (%s) | Amount: %.2f | New Balance: %.2f",
                type, playerName, player.toString(), amount, newBalance);
        log("api", message);
    }

    /**
     * Log a generic transaction
     */
    public void logTransaction(String type, UUID player, String playerName,
            String currencyId, double amount, String details) {
        if (!enabled)
            return;

        String message = String.format("[%s] %s (%s) | Currency: %s | Amount: %.2f | %s",
                type, playerName, player.toString(), currencyId, amount, details);
        log("api", message);
    }

    private void log(String category, String message) {
        executor.submit(() -> {
            try {
                LocalDateTime now = LocalDateTime.now();
                // Create category directory if needed: logs/pay, logs/api, etc.
                Path categoryDir = logDirectory.resolve(category);
                if (!Files.exists(categoryDir)) {
                    Files.createDirectories(categoryDir);
                }

                String fileName = category + "-" + now.format(dateFormatter) + ".log";
                Path logFile = categoryDir.resolve(fileName);

                String logLine = String.format("[%s] %s%n", now.format(timeFormatter), message);

                Files.writeString(logFile, logLine,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);

            } catch (IOException e) {
                plugin.getLogger().warning("Failed to write to transaction log (" + category + "): " + e.getMessage());
            }
        });
    }

    /**
     * Shutdown the logger gracefully
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
