package dev.toolkitmc.guiapi.gui;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player last anvil input store.
 *
 * Holds the most recent text entered via an anvil input dialog.
 * Cleared automatically when the player closes a GUI (see {@link BarrelGuiHandler}).
 */
public final class GuiInputStore {

    public static final GuiInputStore INSTANCE = new GuiInputStore();

    private final ConcurrentHashMap<UUID, String> store = new ConcurrentHashMap<>();

    private GuiInputStore() {}

    public void set(UUID player, String value) {
        store.put(player, value != null ? value : "");
    }

    public String get(UUID player) {
        return store.getOrDefault(player, "");
    }

    public void clear(UUID player) {
        store.remove(player);
    }
}
