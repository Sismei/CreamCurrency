package com.agevate.creamcurrency.hook;

import com.agevate.creamcurrency.CreamCurrency;
import com.agevate.creamcurrency.cache.BalanceCache;
import com.agevate.creamcurrency.currency.Currency;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.DecimalFormat;

/**
 * PlaceholderAPI expansion for CreamCurrency.
 * Uses cache for fast placeholder resolution without blocking.
 */
public class CreamPlaceholderExpansion extends PlaceholderExpansion {

    private final CreamCurrency plugin;
    private final DecimalFormat compactFormat = new DecimalFormat("#,##0.##");

    public CreamPlaceholderExpansion(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "creamcurrency";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        BalanceCache cache = plugin.getPlayerDataDAO().getCache();

        // %creamcurrency_balance_<currency>%
        if (params.startsWith("balance_")) {
            String currencyId = params.substring(8);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            if (currency == null)
                return "N/A";

            // Try cache first (instant)
            Double cached = cache.get(player.getUniqueId(), currencyId);
            if (cached != null) {
                return currency.format(cached);
            }

            // Trigger async load and return placeholder
            plugin.getPlayerDataDAO().getBalance(player.getUniqueId(), currencyId);
            return "...";
        }

        // %creamcurrency_raw_balance_<currency>%
        if (params.startsWith("raw_balance_")) {
            String currencyId = params.substring(12);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            if (currency == null)
                return "0";

            Double cached = cache.get(player.getUniqueId(), currencyId);
            if (cached != null) {
                return compactFormat.format(cached);
            }

            plugin.getPlayerDataDAO().getBalance(player.getUniqueId(), currencyId);
            return "0";
        }

        // %creamcurrency_formatted_<currency>% - Compact format (1K, 1M, etc)
        if (params.startsWith("formatted_")) {
            String currencyId = params.substring(10);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            if (currency == null)
                return "N/A";

            Double cached = cache.get(player.getUniqueId(), currencyId);
            if (cached != null) {
                return formatCompact(cached) + currency.getSymbol();
            }

            plugin.getPlayerDataDAO().getBalance(player.getUniqueId(), currencyId);
            return "...";
        }

        // %creamcurrency_symbol_<currency>%
        if (params.startsWith("symbol_")) {
            String currencyId = params.substring(7);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            return currency != null ? currency.getSymbol() : "";
        }

        // %creamcurrency_name_<currency>%
        if (params.startsWith("name_")) {
            String currencyId = params.substring(5);
            Currency currency = plugin.getCurrencyManager().getCurrency(currencyId);
            return currency != null ? currency.getName() : "Unknown";
        }

        return null;
    }

    private String formatCompact(double value) {
        if (value >= 1_000_000_000_000L) {
            return compactFormat.format(value / 1_000_000_000_000L) + "T";
        } else if (value >= 1_000_000_000) {
            return compactFormat.format(value / 1_000_000_000) + "B";
        } else if (value >= 1_000_000) {
            return compactFormat.format(value / 1_000_000) + "M";
        } else if (value >= 1_000) {
            return compactFormat.format(value / 1_000) + "K";
        }
        return compactFormat.format(value);
    }
}
