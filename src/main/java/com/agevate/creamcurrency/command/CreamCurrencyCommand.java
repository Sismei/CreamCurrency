package com.agevate.creamcurrency.command;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.utils.TextUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

/**
 * Admin command for CreamCurrency management.
 */
public class CreamCurrencyCommand implements CommandExecutor, TabCompleter {

    private final CreamCurrency plugin;

    public CreamCurrencyCommand(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            sender.sendMessage(TextUtils.colorize(
                    plugin.getConfig().getString("messages.prefix", "") +
                            plugin.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                long start = System.currentTimeMillis();
                plugin.reloadConfig();
                plugin.getCommandConfig().load();
                plugin.getCurrencyManager().loadCurrencies();
                // Clear balance cache on reload
                plugin.getPlayerDataDAO().getCache().clear();
                long time = System.currentTimeMillis() - start;
                sender.sendMessage(TextUtils.colorize(
                        plugin.getConfig().getString("messages.prefix", "") +
                                plugin.getConfig().getString("messages.reload", "&aConfiguration reloaded.") +
                                " &7(" + time + "ms)"));
                break;
            case "cache":
                if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                    plugin.getPlayerDataDAO().getCache().clear();
                    sender.sendMessage(TextUtils.colorize("&aBalance cache cleared."));
                } else {
                    sender.sendMessage(TextUtils.colorize("&eUsage: /creamcurrency cache clear"));
                }
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtils.colorize("&e&lCreamCurrency &8- &7Admin Commands"));
        sender.sendMessage(TextUtils.colorize("&6/creamcurrency reload &8- &7Reload configuration"));
        sender.sendMessage(TextUtils.colorize("&6/creamcurrency cache clear &8- &7Clear balance cache"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("creamcurrency.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return List.of("reload", "cache");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("cache")) {
            return List.of("clear");
        }
        return Collections.emptyList();
    }
}
