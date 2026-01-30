package com.agevate.creamcurrency.command;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.config.CommandConfig;
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
 * Dynamic currency command that works with any currency based on the command
 * alias.
 * Uses CommandConfig for customizable subcommand aliases.
 */
public class DynamicCurrencyCommand implements CommandExecutor, TabCompleter {

    private final CreamCurrency plugin;

    public DynamicCurrencyCommand(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    private Currency getCurrencyFromAlias(String alias) {
        String lowerAlias = alias.toLowerCase();
        for (Currency currency : plugin.getCurrencyManager().getCurrencies().values()) {
            if (currency.getId().equalsIgnoreCase(lowerAlias)) {
                return currency;
            }
            for (String currencyAlias : currency.getAliases()) {
                if (currencyAlias.equalsIgnoreCase(lowerAlias)) {
                    return currency;
                }
            }
        }
        return null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Currency currency = getCurrencyFromAlias(label);
        if (currency == null) {
            sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                    .getString("messages.currency-not-found-cmd", "&cKomut için para birimi bulunamadı: /%label%")
                    .replace("%label%", label)));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(TextUtils.colorize(plugin.getConfig().getString("messages.console-not-allowed",
                        "&cKonsol bir oyuncu belirtmelidir.")));
                return true;
            }
            checkBalance(sender, player, currency);
            return true;
        }

        String sub = args[0];
        CommandConfig cmdConfig = plugin.getCommandConfig();
        SubCommand matched = cmdConfig.matchSubCommand(sub);

        if (matched == null) {
            // Check if it's a player name (admin checking balance)
            if (sender.hasPermission("creamcurrency.admin")) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(sub);
                checkBalance(sender, target, currency);
            } else {
                sendHelp(sender, label, currency);
            }
            return true;
        }

        switch (matched) {
            case PAY -> handlePay(sender, args, currency);
            case TOP -> handleTop(sender, currency, args);
            case GIVE -> handleGive(sender, args, currency);
            case SET -> handleSet(sender, args, currency);
            case REMOVE -> handleRemove(sender, args, currency);
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
                    checkBalance(sender, target, currency);
                } else if (sender instanceof Player player) {
                    checkBalance(sender, player, currency);
                } else {
                    sender.sendMessage(TextUtils.colorize(plugin.getConfig().getString("messages.console-not-allowed",
                            "&cKonsol bir oyuncu belirtmelidir.")));
                }
            }
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

    private void handlePay(CommandSender sender, String[] args, Currency currency) {
        // Check if currency allows payments
        if (!currency.isPayable()) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.pay-disabled",
                            "&cPayments are disabled for this currency.")));
            return;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.only-players", "&cSadece oyuncular ödeme yapabilir.")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.pay-usage", "&cKullanım: /%currency% pay <oyuncu> <miktar>")
                            .replace("%currency%", currency.getId())));
            return;
        }

        // Allow payments to offline players too
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

        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(TextUtils
                    .colorize(plugin.getConfig().getString("messages.cannot-pay-self", "&cKendine ödeme yapamazsın.")));
            return;
        }

        double amount;
        try {
            amount = NumberUtils.parseAmount(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(
                    TextUtils.colorize(plugin.getConfig().getString("messages.invalid-amount", "&cGeçersiz miktar.")));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(TextUtils
                    .colorize(plugin.getConfig().getString("messages.amount-positive", "&cMiktar pozitif olmalıdır.")));
            return;
        }

        final String targetName = target.getName() != null ? target.getName() : "Unknown";

        // Check if receiver has disabled payments
        plugin.getPlayerDataDAO().isPaymentsDisabled(target.getUniqueId()).thenAccept(disabled -> {
            if (disabled) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.pay-locked", "&cBu oyuncu ödemeleri kapattı.")));
                return;
            }

            plugin.getPlayerDataDAO().removeBalance(player.getUniqueId(), currency.getId(), amount)
                    .thenAccept(newSenderBalance -> {
                        if (newSenderBalance < 0) {
                            sender.sendMessage(TextUtils.colorize(
                                    plugin.getConfig().getString("messages.insufficient-funds",
                                            "&cInsufficient funds.")));
                            return;
                        }

                        plugin.getPlayerDataDAO().addBalance(target.getUniqueId(), currency.getId(), amount)
                                .thenAccept(newTargetBalance -> {
                                    // Log the payment
                                    plugin.getTransactionLogger().logPayment(
                                            player.getUniqueId(), player.getName(),
                                            target.getUniqueId(), targetName,
                                            currency.getId(), amount);

                                    String formattedAmount = currency.format(amount);

                                    // Run on main thread for proper message delivery
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        // 1. Notify Sender
                                        String sentChat = plugin.getConfig()
                                                .getString("messages.pay.sent-chat", "&aSent %amount% to %player%.")
                                                .replace("%amount%", formattedAmount)
                                                .replace("%player%", targetName);
                                        player.sendMessage(TextUtils.colorize(sentChat));

                                        String sentBar = plugin.getConfig().getString("messages.pay.sent-actionbar",
                                                "");
                                        if (sentBar != null && !sentBar.isBlank()) {
                                            TextUtils.sendActionBar(player, sentBar
                                                    .replace("%amount%", formattedAmount)
                                                    .replace("%player%", targetName));
                                        }

                                        String sentSound = plugin.getConfig().getString("messages.pay.sounds.sent", "");
                                        TextUtils.playSound(player, sentSound);

                                        // Notify target if online
                                        Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
                                        if (targetPlayer != null && targetPlayer.isOnline()) {
                                            String senderName = player.getName();

                                            String receivedChat = plugin.getConfig().getString(
                                                    "messages.pay.received-chat",
                                                    "&aReceived %amount% from %player%.");
                                            targetPlayer.sendMessage(TextUtils.colorize(receivedChat
                                                    .replace("%amount%", formattedAmount)
                                                    .replace("%player%", senderName)));

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

        CommandConfig cmdConfig = plugin.getCommandConfig();
        final int limit = 10;
        final int finalPage = page;
        final int offset = (page - 1) * limit;

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

    private void handleGive(CommandSender sender, String[] args, Currency currency) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                    .getString("messages.give-usage", "&cKullanım: /%currency% give <oyuncu> <miktar>")
                    .replace("%currency%", currency.getId())));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(
                    TextUtils.colorize(plugin.getConfig().getString("messages.invalid-amount", "&cGeçersiz miktar.")));
            return;
        }

        plugin.getPlayerDataDAO().addBalance(target.getUniqueId(), currency.getId(), amount)
                .thenAccept(newBalance -> {
                    // Log the admin give
                    plugin.getTransactionLogger().logAdminGive(
                            sender.getName(), target.getUniqueId(),
                            target.getName() != null ? target.getName() : "Unknown",
                            currency.getId(), amount, newBalance);

                    sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                            .getString("messages.admin-give-success",
                                    "&a%player% kişisine %amount% verildi. &7(Yeni: %new_balance%)")
                            .replace("%player%", target.getName() != null ? target.getName() : "Unknown")
                            .replace("%amount%", currency.format(amount))
                            .replace("%new_balance%", currency.format(newBalance))));
                });
    }

    private void handleSet(CommandSender sender, String[] args, Currency currency) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.set-usage", "&cKullanım: /%currency% set <oyuncu> <miktar>")
                            .replace("%currency%", currency.getId())));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(
                    TextUtils.colorize(plugin.getConfig().getString("messages.invalid-amount", "&cGeçersiz miktar.")));
            return;
        }

        plugin.getPlayerDataDAO().setBalance(target.getUniqueId(), currency.getId(), amount)
                .thenRun(() -> {
                    sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                            .getString("messages.admin-set-success", "&a%player% kişisinin bakiyesi %amount% yapıldı.")
                            .replace("%player%", target.getName() != null ? target.getName() : "Unknown")
                            .replace("%amount%", currency.format(amount))));
                });
    }

    private void handleRemove(CommandSender sender, String[] args, Currency currency) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }

        if (args.length < 3) {
            sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                    .getString("messages.remove-usage", "&cKullanım: /%currency% remove <oyuncu> <miktar>")
                    .replace("%currency%", currency.getId())));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(
                    TextUtils.colorize(plugin.getConfig().getString("messages.invalid-amount", "&cGeçersiz miktar.")));
            return;
        }

        plugin.getPlayerDataDAO().getBalance(target.getUniqueId(), currency.getId())
                .thenAccept(current -> {
                    double newBalance = Math.max(0, current - amount);
                    plugin.getPlayerDataDAO().setBalance(target.getUniqueId(), currency.getId(), newBalance)
                            .thenRun(() -> {
                                // Log the admin remove
                                plugin.getTransactionLogger().logAdminRemove(
                                        sender.getName(), target.getUniqueId(),
                                        target.getName() != null ? target.getName() : "Unknown",
                                        currency.getId(), amount, newBalance);

                                sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                                        .getString("messages.admin-remove-success",
                                                "&a%player% kişisinden %amount% silindi. &7(Yeni: %new_balance%)")
                                        .replace("%player%", target.getName() != null ? target.getName() : "Unknown")
                                        .replace("%amount%", currency.format(amount))
                                        .replace("%new_balance%", currency.format(newBalance))));
                            });
                });
    }

    private void sendHelp(CommandSender sender, String label, Currency currency) {
        sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                .getString("messages.help.header", "&e&l----- /%label% -----").replace("%label%", label)));
        sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                .getString("messages.help.balance", "&6/%label% &8- &7Bakiyeni gör").replace("%label%", label)));
        // Only show pay if currency allows it
        if (currency.isPayable()) {
            sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                    .getString("messages.help.pay", "&6/%label% pay <oyuncu> <miktar> &8- &7Para gönder")
                    .replace("%label%", label)));
        }
        sender.sendMessage(TextUtils.colorize(plugin.getConfig()
                .getString("messages.help.top", "&6/%label% top &8- &7Sıralamayı gör").replace("%label%", label)));
        if (sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.help.admin-give", "&6/%label% give <oyuncu> <miktar>")
                            .replace("%label%", label)));
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.help.admin-set", "&6/%label% set <oyuncu> <miktar>")
                            .replace("%label%", label)));
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.help.admin-remove", "&6/%label% remove <oyuncu> <miktar>")
                            .replace("%label%", label)));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Currency currency = getCurrencyFromAlias(alias);
        CommandConfig cmdConfig = plugin.getCommandConfig();

        if (args.length == 1) {
            List<String> completions = new java.util.ArrayList<>();
            completions.add("balance");

            // Only add pay if currency is payable
            if (currency != null && currency.isPayable()) {
                completions.add("pay");
            }

            completions.add("top");
            completions.add("toggle");

            if (sender.hasPermission("creamcurrency.admin")) {
                completions.add("give");
                completions.add("set");
                completions.add("remove");
            }

            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            SubCommand matched = cmdConfig.matchSubCommand(sub);

            // Show player names for subcommands that need a player argument
            if (matched == SubCommand.PAY || matched == SubCommand.GIVE ||
                    matched == SubCommand.SET || matched == SubCommand.REMOVE ||
                    matched == SubCommand.BALANCE) {

                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return Collections.emptyList();
    }
}
