package com.agevate.creamcurrency.cache;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory cache for player balances.
 * Reduces database queries significantly for frequently accessed data.
 */
public class BalanceCache {

    // Key format: "uuid:currencyId"
    private final Map<String, Double> cache = new ConcurrentHashMap<>();

    private String key(UUID uuid, String currencyId) {
        return uuid.toString() + ":" + currencyId;
    }

    public Double get(UUID uuid, String currencyId) {
        return cache.get(key(uuid, currencyId));
    }

    public void set(UUID uuid, String currencyId, double balance) {
        cache.put(key(uuid, currencyId), balance);
    }

    public void invalidate(UUID uuid, String currencyId) {
        cache.remove(key(uuid, currencyId));
    }

    public void invalidatePlayer(UUID uuid) {
        String prefix = uuid.toString() + ":";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    public void clear() {
        cache.clear();
    }

    public boolean contains(UUID uuid, String currencyId) {
        return cache.containsKey(key(uuid, currencyId));
    }
}
