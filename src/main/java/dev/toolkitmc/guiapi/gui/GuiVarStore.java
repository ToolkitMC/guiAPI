package dev.toolkitmc.guiapi.gui;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player runtime variable store.
 *
 * Variables are:
 *  - String key → String value
 *  - Scoped to a player UUID
 *  - Held in memory only; cleared on GUI close by default (see {@link #clear(UUID)})
 *  - Not persisted across server restarts
 *
 * Numeric operations (add_var, sub_var) parse the current value as int.
 * If the current value is not a valid int, it is treated as 0.
 */
public final class GuiVarStore {

    public static final GuiVarStore INSTANCE = new GuiVarStore();

    /** player UUID → { key → value } */
    private final ConcurrentHashMap<UUID, HashMap<String, String>> store = new ConcurrentHashMap<>();

    private GuiVarStore() {}

    // ── Write ────────────────────────────────────────────────────────────────

    /** Set a variable to a string value. */
    public void set(UUID player, String key, String value) {
        store.computeIfAbsent(player, k -> new HashMap<>()).put(key, value);
    }

    /** Add an integer delta to a variable (default 0 if unset or non-numeric). */
    public void add(UUID player, String key, int delta) {
        int current = getInt(player, key);
        set(player, key, String.valueOf(current + delta));
    }

    /** Remove a single variable. */
    public void remove(UUID player, String key) {
        Map<String, String> map = store.get(player);
        if (map != null) map.remove(key);
    }

    /** Remove all variables for a player. */
    public void clear(UUID player) {
        store.remove(player);
    }

    // ── Read ─────────────────────────────────────────────────────────────────

    /** Get a variable as String, or {@code null} if unset. */
    public String get(UUID player, String key) {
        Map<String, String> map = store.get(player);
        return map != null ? map.get(key) : null;
    }

    /** Get a variable as String with a fallback default. */
    public String getOrDefault(UUID player, String key, String def) {
        String v = get(player, key);
        return v != null ? v : def;
    }

    /** Get a variable as int (0 if unset or non-numeric). */
    public int getInt(UUID player, String key) {
        String v = get(player, key);
        if (v == null) return 0;
        try { return Integer.parseInt(v); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Snapshot of all variables for a player (unmodifiable). */
    public Map<String, String> getAll(UUID player) {
        Map<String, String> map = store.get(player);
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

    /** Total number of players with active variable maps. */
    public int playerCount() {
        return store.size();
    }
}
