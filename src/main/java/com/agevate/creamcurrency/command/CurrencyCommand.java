package com.agevate.creamcurrency.command;

import com.agevate.creamcurrency.CreamCurrency;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Multi-currency commands (/currency).
 */
public class CurrencyCommand implements CommandExecutor, TabCompleter {

    private final CreamCurrency plugin;

    public CurrencyCommand(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 1) {
            sendHelp(sender);
            return true;
        }

        // Check if the command label itself is a currency (e.g. /coin, /gold)
        Currency labelCurrency = plugin.getCurrencyManager().getCurrency(label);

        String subCommandName = args[0].toLowerCase();

        // If label is a currency, we inject it into the args for handlers that expect
        // it
        // Or we handle it specifically. To avoid rewriting all handlers, let's adapt.

        switch (subCommandName) {
            case "balance", "bal" -> {
                if (labelCurrency != null) {
                    // /coin bal [player] -> effective: /currency bal coin [player]
                    handleBalance(sender, labelCurrency, args, 1);
                } else {
                    handleBalance(sender, null, args, 1);
                }
            }
            case "pay" -> {
                if (labelCurrency != null) {
                    // /coin pay <player> <amount> -> effective: /currency pay coin <player>
                    // <amount>
                    handlePay(sender, labelCurrency, args, 1);
                } else {
                    handlePay(sender, null, args, 1);
                }
            }
            case "top", "baltop" -> {
                if (labelCurrency != null) {
                    handleTop(sender, labelCurrency, args, 1);
                } else {
                    handleTop(sender, null, args, 1);
                }
            }
            case "toggle" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(TextUtils.colorize("&cOnly players can toggle payments."));
                    return true;
                }
                handleToggle(player);
            }
            case "give" -> handleGive(sender, args);
            case "set" -> handleSet(sender, args);
            case "remove", "take" -> handleRemove(sender, args);
            case "list" -> handleList(sender);
            default -> sendHelp(sender);
        }

        return true;
    }

    // Adapt handleBalance to take optional pre-resolved currency and offset
    private void handleBalance(CommandSender sender, Currency preResolved, String[] args, int offset) {
        Currency currency = preResolved;
        int playerIndex = offset; // if preResolved is set, player is at offset, valid if args.length > offset

        if (currency == null) {
            if (args.length <= offset) {
                sender.sendMessage(TextUtils.colorize("&cUsage: /currency balance <currency> [player]"));
                return;
            }
            currency = plugin.getCurrencyManager().getCurrency(args[offset]);
            if (currency == null) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
                return;
            }
            playerIndex++;
        }

        OfflinePlayer target;
        if (args.length > playerIndex) {
            if (!sender.hasPermission("creamcurrency.admin")) {
                sender.sendMessage(
                        TextUtils.colorize(plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
                return;
            }
            target = Bukkit.getOfflinePlayer(args[playerIndex]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(TextUtils.colorize("&cConsole must specify a player."));
            return;
        }

        Currency finalCurrency = currency;
        plugin.getPlayerDataDAO().getBalance(target.getUniqueId(), currency.getId()).thenAccept(balance -> {
            String msg = plugin.getConfig().getString("messages.balance", "&7Balance: &f%balance%")
                    .replace("%balance%", finalCurrency.format(balance));
            sender.sendMessage(TextUtils.colorize(msg));
        });
    }

    private void handlePay(CommandSender sender, Currency preResolved, String[] args, int offset) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(TextUtils.colorize("&cOnly players can pay."));
            return;
        }

        Currency currency = preResolved;
        int nextArg = offset;

        // If currency not resolved, read it from args
        if (currency == null) {
            if (args.length <= nextArg) {
                sender.sendMessage(TextUtils.colorize("&cUsage: /currency pay <currency> <player> <amount>"));
                return;
            }
            currency = plugin.getCurrencyManager().getCurrency(args[nextArg]);
            if (currency == null) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
                return;
            }
            nextArg++;
        }

        // Now we expect <player> <amount>
        if (args.length < nextArg + 2) {
            sender.sendMessage(TextUtils
                    .colorize("&cUsage: " + (preResolved != null ? "/" + preResolved.getId() + " pay <player> <amount>"
                            : "/currency pay <currency> <player> <amount>")));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[nextArg]);
        nextArg++;

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(TextUtils
                    .colorize(plugin.getConfig().getString("messages.player-not-found", "&cPlayer not found.")));
            return;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            sender.sendMessage(TextUtils.colorize("&cYou cannot pay yourself."));
            return;
        }

        double amount;
        try {
            amount = NumberUtils.parseAmount(args[nextArg]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtils.colorize("&cInvalid amount."));
            return;
        }

        if (amount <= 0) {
            sender.sendMessage(TextUtils.colorize("&cAmount must be positive."));
            return;
        }

        Currency finalCurrency = currency;
        // Check if receiver has disabled payments
        plugin.getPlayerDataDAO().isPaymentsDisabled(target.getUniqueId()).thenAccept(disabled -> {
            if (disabled) {
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.pay-locked", "&cBu oyuncu ödemeleri kapattı.")));
                return;
            }

            plugin.getPlayerDataDAO().removeBalance(player.getUniqueId(), finalCurrency.getId(), amount)
                    .thenAccept(newBalance -> {
                        if (newBalance < 0) {
                            player.sendMessage(TextUtils.colorize(plugin.getConfig()
                                    .getString("messages.insufficient-funds", "&cInsufficient funds.")));
                            return;
                        }

                        plugin.getPlayerDataDAO().addBalance(target.getUniqueId(), finalCurrency.getId(), amount)
                                .thenAccept(targetNewBalance -> {
                                    // Log the payment
                                    plugin.getTransactionLogger().logPayment(
                                            player.getUniqueId(), player.getName(),
                                            target.getUniqueId(),
                                            target.getName() != null ? target.getName() : "Unknown",
                                            finalCurrency.getId(), amount);

                                    String formattedAmount = finalCurrency.format(amount);
                                    String targetDispName = target.getName() != null ? target.getName() : "Unknown";
                                    String senderName = player.getName();

                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        // 1. Notify Sender
                                        String sentChat = plugin.getConfig().getString("messages.pay.sent-chat",
                                                plugin.getConfig().getString("messages.pay-sent",
                                                        "&aSent %amount% to %player%."))
                                                .replace("%amount%", formattedAmount)
                                                .replace("%player%", targetDispName);
                                        player.sendMessage(TextUtils.colorize(sentChat));

                                        String sentBar = plugin.getConfig().getString("messages.pay.sent-actionbar",
                                                "");
                                        if (sentBar != null && !sentBar.isBlank()) {
                                            TextUtils.sendActionBar(player, sentBar.replace("%amount%", formattedAmount)
                                                    .replace("%player%", targetDispName));
                                        }

                                        String sentSound = plugin.getConfig().getString("messages.pay.sounds.sent", "");
                                        TextUtils.playSound(player, sentSound);

                                        // 2. Notify Receiver
                                        Player targetPlayer = Bukkit.getPlayer(target.getUniqueId());
                                        if (targetPlayer != null && targetPlayer.isOnline()) {
                                            String receivedChat = plugin
                                                    .getConfig().getString("messages.pay.received-chat",
                                                            plugin.getConfig().getString("messages.pay-received",
                                                                    "&aReceived %amount% from %player%."))
                                                    .replace("%amount%", formattedAmount)
                                                    .replace("%player%", senderName);
                                            targetPlayer.sendMessage(TextUtils.colorize(receivedChat));

                                            String receivedBar = plugin.getConfig()
                                                    .getString("messages.pay.received-actionbar", "");
                                            if (receivedBar != null && !receivedBar.isBlank()) {
                                                TextUtils.sendActionBar(targetPlayer,
                                                        receivedBar.replace("%amount%", formattedAmount)
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

    private void handleTop(CommandSender sender, Currency preResolved, String[] args, int offset) {
        // Usage: /currency top [currency] [page] or /coin top [page]
        Currency currency = preResolved;
        int nextArg = offset;

        if (currency == null) {
            if (args.length > nextArg) {
                Currency tryCurr = plugin.getCurrencyManager().getCurrency(args[nextArg]);
                if (tryCurr != null) {
                    currency = tryCurr;
                    nextArg++;
                } else {
                    // Argument provided but not a currency, maybe it's a page number?
                    // If no currency specified, use default/primary? Or error?
                    // Let's default to primary if exists
                    currency = plugin.getCurrencyManager().getPrimaryCurrency();
                }
            } else {
                currency = plugin.getCurrencyManager().getPrimaryCurrency();
            }
        }

        if (currency == null) {
            sender.sendMessage(TextUtils
                    .colorize(plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
            return;
        }

        int page = 1;
        if (args.length > nextArg) {
            try {
                page = Integer.parseInt(args[nextArg]);
            } catch (NumberFormatException ignored) {
            }
        }
        if (page < 1)
            page = 1;

        final int limit = 10;
        final int finalPage = page;
        final Currency finalCurrency = currency;
        final int dbOffset = (page - 1) * limit;

        // Async Fetch
        sender.sendMessage(TextUtils.colorize("&7Loading..."));

        plugin.getPlayerDataDAO().getTotalBalance(finalCurrency.getId()).thenAccept(totalBalance -> {
            plugin.getPlayerDataDAO().getTopBalancesWithNames(finalCurrency.getId(), limit, dbOffset)
                    .thenAccept(topList -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {

                            // Header
                            String header = plugin.getCommandConfig().getTopHeader()
                                    .replace("%currency%", finalCurrency.getName())
                                    .replace("%page%", String.valueOf(finalPage));

                            // Total Supply & Player Count (Approximated or separate query?)
                            // For now just Total Supply as requested
                            sender.sendMessage(TextUtils.colorize(header));
                            sender.sendMessage(
                                    TextUtils.colorize("&7Total Economy: &a" + finalCurrency.format(totalBalance)));
                            // To do strictly "Player Count" I'd need separate count query.
                            // I will assume for now just total balance is enough or add a quick count query
                            // if strictly needed.

                            if (topList.isEmpty()) {
                                sender.sendMessage(TextUtils.colorize(plugin.getCommandConfig().getTopEmpty()));
                            } else {
                                int rank = dbOffset + 1;
                                for (var entry : topList) {
                                    // Get name from database first, fallback to Bukkit if null
                                    String pName = entry.name();
                                    if (pName == null || pName.isEmpty()) {
                                        OfflinePlayer p = Bukkit.getOfflinePlayer(entry.uuid());
                                        pName = p.getName() != null ? p.getName() : "Unknown";
                                    }
                                    String line = plugin.getCommandConfig().getTopEntry()
                                            .replace("%rank%", String.valueOf(rank))
                                            .replace("%player%", pName)
                                            .replace("%balance%", finalCurrency.format(entry.balance()))
                                            .replace("%color%", plugin.getCommandConfig().getTopColor(rank));
                                    sender.sendMessage(TextUtils.colorize(line));
                                    rank++;
                                }
                            }
                            sender.sendMessage(TextUtils.colorize("&7Page: " + finalPage));
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

    private void handleGive(CommandSender sender, String[] args) {
        // Admin logs handled here
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(TextUtils.colorize("&cUsage: /currency give <currency> <player> <amount>"));
            return;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(args[1]);
        if (currency == null) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtils.colorize("&cInvalid amount."));
            return;
        }

        plugin.getPlayerDataDAO().addBalance(target.getUniqueId(), currency.getId(), amount)
                .thenAccept(newBalance -> {
                    sender.sendMessage(TextUtils.colorize("&aGave " + currency.format(amount) +
                            " to " + target.getName() + " &7(New: " + currency.format(newBalance) + ")"));
                    plugin.getTransactionLogger().logAdminGive(sender.getName(), target.getUniqueId(), target.getName(),
                            currency.getId(), amount, newBalance);
                });
    }

    private void handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }
        if (args.length < 4) {
            sender.sendMessage(TextUtils.colorize("&cUsage: /currency set <currency> <player> <amount>"));
            return;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(args[1]);
        if (currency == null) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtils.colorize("&cInvalid amount."));
            return;
        }

        // Need old balance for log?
        plugin.getPlayerDataDAO().getBalance(target.getUniqueId(), currency.getId()).thenAccept(oldBal -> {
            plugin.getPlayerDataDAO().setBalance(target.getUniqueId(), currency.getId(), amount)
                    .thenRun(() -> {
                        sender.sendMessage(TextUtils.colorize("&aSet " + target.getName() +
                                "'s balance to " + currency.format(amount)));
                        plugin.getTransactionLogger().logAdminSet(sender.getName(), target.getUniqueId(),
                                target.getName(), currency.getId(), oldBal, amount);
                    });
        });
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return;
        }

        if (args.length < 4) {
            sender.sendMessage(TextUtils.colorize("&cUsage: /currency remove <currency> <player> <amount>"));
            return;
        }

        Currency currency = plugin.getCurrencyManager().getCurrency(args[1]);
        if (currency == null) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.currency-not-found", "&cCurrency not found.")));
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        double amount;
        try {
            amount = NumberUtils.parseAmount(args[3]);
        } catch (NumberFormatException e) {
            sender.sendMessage(TextUtils.colorize("&cInvalid amount."));
            return;
        }

        plugin.getPlayerDataDAO().getBalance(target.getUniqueId(), currency.getId())
                .thenAccept(current -> {
                    double newBalance = Math.max(0, current - amount);
                    plugin.getPlayerDataDAO().setBalance(target.getUniqueId(), currency.getId(), newBalance)
                            .thenRun(() -> {
                                sender.sendMessage(TextUtils.colorize("&aRemoved " + currency.format(amount) +
                                        " from " + target.getName() + " &7(New: " + currency.format(newBalance) + ")"));
                                plugin.getTransactionLogger().logAdminRemove(sender.getName(), target.getUniqueId(),
                                        target.getName(), currency.getId(), amount, newBalance);
                            });
                });
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(TextUtils.colorize("&e&lLoaded Currencies:"));
        plugin.getCurrencyManager().getCurrencies().forEach((id, curr) -> {
            sender.sendMessage(TextUtils.colorize("&6- &f" + curr.getName() + " &7(" + id + ")"));
        });
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtils.colorize("&e&l----- CreamCurrency -----"));
        sender.sendMessage(TextUtils.colorize("&6/currency balance <type> [player]"));
        sender.sendMessage(TextUtils.colorize("&6/currency pay <type> <player> <amount>"));
        sender.sendMessage(TextUtils.colorize("&6/currency top <type>"));
        if (sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize("&6/currency give <type> <player> <amount>"));
            sender.sendMessage(TextUtils.colorize("&6/currency set <type> <player> <amount>"));
            sender.sendMessage(TextUtils.colorize("&6/currency remove <type> <player> <amount>"));
            sender.sendMessage(TextUtils.colorize("&6/currency list"));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        Currency aliasCurrency = plugin.getCurrencyManager().getCurrency(alias);

        // args[0] is always the first argument after command.
        // If /coin pay -> args[0]="pay"

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>(List.of("balance", "pay", "top", "toggle"));
            if (sender.hasPermission("creamcurrency.admin")) {
                subcommands.addAll(List.of("give", "set", "remove", "list"));
            }
            return filter(subcommands, args[0]);
        }

        String sub = args[0].toLowerCase();

        // If using /currency, expected: /currency pay <curr> <player>
        // If using /coin, expected: /coin pay <player>

        if (aliasCurrency == null) {
            // Standard behavior
            if (args.length == 2 && !sub.equals("list")) {
                return filter(new ArrayList<>(plugin.getCurrencyManager().getCurrencies().keySet()), args[1]);
            }
            if (args.length == 3 && (sub.equals("pay") || sub.equals("give") || sub.equals("set")
                    || sub.equals("remove") || sub.equals("balance"))) {
                return null; // Players
            }
        } else {
            // Alias behavior
            // args[0]=pay, args[1]=<player>
            if (args.length == 2 && (sub.equals("pay") || sub.equals("give") || sub.equals("set")
                    || sub.equals("remove") || sub.equals("balance"))) {
                return null; // Players
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String query) {
        String q = query.toLowerCase();
        return list.stream().filter(s -> s.toLowerCase().startsWith(q)).collect(Collectors.toList());
    }
}
