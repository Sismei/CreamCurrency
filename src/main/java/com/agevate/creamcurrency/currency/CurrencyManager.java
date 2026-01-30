package com.agevate.creamcurrency.currency;

import com.agevate.creamcurrency.CreamCurrency;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class CurrencyManager {

    private final CreamCurrency plugin;
    private final Map<String, Currency> currencies = new HashMap<>();
    private Currency primaryCurrency;

    public CurrencyManager(CreamCurrency plugin) {
        this.plugin = plugin;
    }

    public void loadCurrencies() {
        currencies.clear();
        File currenciesFolder = new File(plugin.getDataFolder(), "currencies");

        if (!currenciesFolder.exists()) {
            currenciesFolder.mkdirs();
            // Save default money.yml if empty
            plugin.saveResource("currencies/money.yml", false);
        }

        File[] files = currenciesFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null)
            return;

        for (File file : files) {
            String id = file.getName().replace(".yml", "");
            try {
                Currency currency = new Currency(id, file);
                currencies.put(id, currency);
                plugin.getLogger().info("Loaded currency: " + currency.getName() + " (" + id + ")");
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load currency file: " + file.getName(), e);
            }
        }

        setupPrimaryCurrency();
    }

    private void setupPrimaryCurrency() {
        String primaryId = plugin.getConfig().getString("primary-currency", "money");
        if (currencies.containsKey(primaryId)) {
            primaryCurrency = currencies.get(primaryId);
            plugin.getLogger().info("Primary currency set to: " + primaryCurrency.getName());
        } else {
            plugin.getLogger().severe("Primary currency '" + primaryId + "' not found in loaded currencies!");
            // Fallback to first loaded or null
            if (!currencies.isEmpty()) {
                primaryCurrency = currencies.values().iterator().next();
                plugin.getLogger().warning("Falling back to: " + primaryCurrency.getName());
            }
        }
    }

    public Currency getCurrency(String id) {
        return currencies.get(id);
    }

    public Currency getPrimaryCurrency() {
        return primaryCurrency;
    }

    public Map<String, Currency> getCurrencies() {
        return currencies;
    }
}
