package com.agevate.creamcurrency.command;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.config.CommandConfig.SubCommand;
import com.agevate.creamcurrency.currency.Currency;
import com.agevate.creamcurrency.utils.NumberUtils;
import com.agevate.creamcurrency.utils.TextUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Primary currency commands (/money).
 */
public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final CreamCurrency plugin;

    public MoneyCommand(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Currency primary = plugin.getCurrencyManager().getPrimaryCurrency();
        if (primary == null) {
            sender.sendMessage(TextUtils.colorize("&cPrimary currency not configured."));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtils.colorize("&cConsole must specify a player."));
                return true;
            }
            checkBalance(sender, player, primary);
            return true;
        }

        String sub = args[0];
        var cmdConfig = plugin.getCommandConfig();
        SubCommand matched = cmdConfig.matchSubCommand(sub);

        if (matched == null) {
            if (sender.hasPermission("creamcurrency.admin")) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(sub);
                checkBalance(sender, target, primary);
            } else {
                sender.sendMessage(TextUtils.colorize("&cUsage: /money or /money pay <player> <amount>"));
            }
            return true;
        }

        switch (matched) {
            case PAY -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtils.colorize("&cOnly players can pay."));
                    return true;
                }
                handlePay(player, args, primary);
            }
            case TOP -> handleTop(sender, primary, args);
            case TOGGLE -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtils.colorize("&cOnly players can toggle payments."));
                    return true;
                }
                handleToggle(player);
            }
            case BALANCE -> {
                if (args.length > 1 && sender.hasPermission("creamcurrency.admin")) {
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    checkBalance(sender, target, primary);
                } else if (sender instanceof Player player) {
                    checkBalance(sender, player, primary);
                } else {
                    sender.sendMessage(TextUtils.colorize("&cConsole must specify a player."));
                }
            }
            default ->
                sender.sendMessage(TextUtils.colorize("&cUsage: /money balance or /money pay <player> <amount>"));
        }

        return true;
    }

    private void checkBalance(CommandSender viewer, OfflinePlayer target, Currency currency) {
        plugin.getPlayerDataDAO().getBalance(target.getUniqueId(), currency.getId()).thenAccept(balance -> {
            if (viewer instanceof Player && !viewer.equals(target)) {
                // Viewing another player's balance - use currency-specific message
                String msg = currency.getBalanceOther()
                        .replace("%player%", target.getName() != null ? target.getName() : "Unknown")
                        .replace("%balance%", currency.format(balance));
                viewer.sendMessage(TextUtils.colorize(msg));
            } else {
                // Viewing own balance
                String msg = plugin.getConfig().getString("messages.balance", "&7Bakiye: &f%balance%")
                        .replace("%balance%", currency.format(balance));
                viewer.sendMessage(TextUtils.colorize(msg));
            }
        });
    }

    private void handlePay(Player sender, String[] args, Currency currency) {
        // Check if currency allows payments
        if (!currency.isPayable()) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.pay-disabled",
                            "&cPayments are disabled for this currency.")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtils.colorize("&cUsage: /money pay <player> <amount>"));
            return;
        }

        OfflinePlayer target;
        Player onlineTarget = Bukkit.getPlayer(args[1]);

        if (onlineTarget != null) {
            target = onlineTarget;
        } else {
            target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
                return;
            }
        }

        if (target.getUniqueId().equals(sender.getUniqueId())) {
            sender.sendMessage(TextUtils.colorize("&cYou cannot pay yourself."));
            return;
        }

        double amount;
        try {
            amount = NumberUtils.parseAmount(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtils.colorize("&cInvalid amount."));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(TextUtils.colorize("&cAmount must be positive."));
            return;
        }

        // Check if receiver has disabled payments
        plugin.getPlayerDataDAO().isPaymentsDisabled(target.getUniqueId()).thenAccept(disabled -> {
            if (disabled) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.pay-locked", "&cBu oyuncu ödemeleri kapattı.")));
                return;
            }

            // Use optimized atomic operations
            plugin.getPlayerDataDAO().removeBalance(sender.getUniqueId(), currency.getId(), amount)
                    .thenAccept(newSenderBalance -> {
                        if (newSenderBalance < 0) {
                            sender.sendMessage(TextUtils.colorize(
                                    plugin.getConfig().getString("messages.insufficient-funds",
                                            "&cInsufficient funds.")));
                            return;
                        }

                        plugin.getPlayerDataDAO().addBalance(target.getUniqueId(), currency.getId(), amount)
                                .thenAccept(newTargetBalance -> {
                                    String targetName = target.getName() != null ? target.getName() : "Unknown";

                                    // Log the payment
                                    plugin.getTransactionLogger().logPayment(
                                            sender.getUniqueId(), sender.getName(),
                                            target.getUniqueId(), targetName,
                                            currency.getId(), amount);

                                    String formattedAmount = currency.format(amount);

                                    // Run on main thread for consistent message delivery
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        // 1. Notify Sender
                                        String sentChat = plugin.getConfig()
                                                .getString("messages.pay.sent-chat", "&aSent %amount% to %player%.")
                                                .replace("%amount%", formattedAmount)
                                                .replace("%player%", targetName);
                                        sender.sendMessage(TextUtils.colorize(sentChat));

                                        String sentBar = plugin.getConfig().getString("messages.pay.sent-actionbar",
                                                "");
                                        if (sentBar != null && !sentBar.isBlank()) {
                                            TextUtils.sendActionBar(sender, sentBar
                                                    .replace("%amount%", formattedAmount)
                                                    .replace("%player%", targetName));
                                        }

                                        String sentSound = plugin.getConfig().getString("messages.pay.sounds.sent", "");
                                        TextUtils.playSound(sender, sentSound);

                                        // 2. Notify Receiver
                                        Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
                                        if (targetPlayer != null && targetPlayer.isOnline()) {
                                            String senderName = sender.getName();
                                            String receivedChat = plugin.getConfig()
                                                    .getString("messages.pay.received-chat",
                                                            "&aReceived %amount% from %player%.")
                                                    .replace("%amount%", formattedAmount)
                                                    .replace("%player%", senderName);
                                            targetPlayer.sendMessage(TextUtils.colorize(receivedChat));

                                            String receivedBar = plugin.getConfig()
                                                    .getString("messages.pay.received-actionbar", "");
                                            if (receivedBar != null && !receivedBar.isBlank()) {
                                                TextUtils.sendActionBar(targetPlayer, receivedBar
                                                        .replace("%amount%", formattedAmount)
                                                        .replace("%player%", senderName));
                                            }

                                            String receivedSound = plugin.getConfig()
                                                    .getString("messages.pay.sounds.received", "");
                                            TextUtils.playSound(targetPlayer, receivedSound);
                                        }
                                    });
                                });
                    });
        });
    }

    private void handleToggle(Player player) {
        plugin.getPlayerDataDAO().togglePayments(player.getUniqueId()).thenAccept(disabled -> {
            String msg = disabled ? plugin.getConfig().getString("messages.pay-toggle-off", "&cÖdemeler kapatıldı.")
                    : plugin.getConfig().getString("messages.pay-toggle-on", "&aÖdemeler açıldı.");
            player.sendMessage(TextUtils.colorize(msg));
        });
    }

    private void handleTop(CommandSender sender, Currency currency, String[] args) {
        int page = 1;
        // args[0] is "top". args[1] might be page.
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (page < 1)
            page = 1;

        final int limit = 10;
        final int finalPage = page;
        final int offset = (page - 1) * limit;

        var cmdConfig = plugin.getCommandConfig();

        sender.sendMessage(TextUtils.colorize(cmdConfig.getTopLoading()));

        plugin.getPlayerDataDAO().getTotalBalance(currency.getId()).thenAccept(total -> {
            plugin.getPlayerDataDAO().getTopBalancesWithNames(currency.getId(), limit, offset).thenAccept(topList -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String header = cmdConfig.getTopHeader()
                            .replace("%currency%", currency.getName())
                            .replace("%limit%", String.valueOf(limit))
                            .replace("%page%", String.valueOf(finalPage));

                    sender.sendMessage(TextUtils.colorize(header));
                    sender.sendMessage(TextUtils.colorize(cmdConfig.getTopTotal()
                            .replace("%amount%", currency.format(total))));

                    if (topList.isEmpty()) {
                        sender.sendMessage(TextUtils.colorize(cmdConfig.getTopEmpty()));
                    } else {
                        int rank = offset + 1;
                        for (var entry : topList) {
                            // Get name from database first, fallback to Bukkit if null
                            String name = entry.name();
                            if (name == null || name.isEmpty()) {
                                OfflinePlayer player = Bukkit.getOfflinePlayer(entry.uuid());
                                name = player.getName() != null ? player.getName()
                                        : entry.uuid().toString().substring(0, 8);
                            }
                            String formatted = currency.format(entry.balance());
                            String color = cmdConfig.getTopColor(rank);

                            String line = cmdConfig.getTopEntry()
                                    .replace("%color%", color)
                                    .replace("%rank%", String.valueOf(rank))
                                    .replace("%player%", name)
                                    .replace("%balance%", formatted);

                            sender.sendMessage(TextUtils.colorize(line));
                            rank++;
                        }
                    }
                    sender.sendMessage(TextUtils.colorize(cmdConfig.getTopPage()
                            .replace("%page%", String.valueOf(finalPage))));
                });
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        var cmdConfig = plugin.getCommandConfig();
        if (args.length == 1) {
            return cmdConfig.getTabCompletions(sender.hasPermission("creamcurrency.admin")).stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0];
            SubCommand matched = cmdConfig.matchSubCommand(sub);
            if (matched == SubCommand.PAY
                    || (matched == SubCommand.BALANCE && sender.hasPermission("creamcurrency.admin"))) {
                return null; // Default player list
            }
        }
        return Collections.emptyList();
    }
}
