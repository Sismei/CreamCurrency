package com.agevate.creamcurrency.currency;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

public class Currency {

    private final String id;
    private final String name;
    private final String symbol;
    private final boolean symbolBefore;
    private final double startBalance;
    private final String format;
    private final List<String> aliases;
    private final boolean payable;
    private final DecimalFormat decimalFormat;
    private final String balanceOther;

    public Currency(String id, File file) {
        this.id = id;
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        this.name = config.getString("name", id);
        this.symbol = config.getString("symbol", "");
        this.symbolBefore = config.getBoolean("symbol-before", true);
        this.startBalance = config.getDouble("start-balance", 0.0);
        this.format = config.getString("format", "#,##0.00");
        this.aliases = config.getStringList("aliases");
        this.payable = config.getBoolean("payable", true);
        this.balanceOther = config.getString("balance-other", "&7%player%'nin " + this.name + "'i: &f%balance%");

        // Initialize DecimalFormat with the configured pattern
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        this.decimalFormat = new DecimalFormat(this.format, symbols);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSymbol() {
        return symbol;
    }

    public boolean isSymbolBefore() {
        return symbolBefore;
    }

    public double getStartBalance() {
        return startBalance;
    }

    public String getFormat() {
        return format;
    }

    public List<String> getAliases() {
        return aliases;
    }

    public boolean isPayable() {
        return payable;
    }

    public String getBalanceOther() {
        return balanceOther;
    }

    /**
     * Formats the amount using the configured format pattern and symbol.
     */
    public String format(double amount) {
        String formattedNumber = decimalFormat.format(amount);
        if (symbolBefore) {
            return symbol + formattedNumber;
        } else {
            return formattedNumber + symbol;
        }
    }
}
