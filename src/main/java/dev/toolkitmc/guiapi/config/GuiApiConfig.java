package dev.toolkitmc.guiapi.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.toolkitmc.guiapi.GuiApiMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persistent config for GUI API.
 * Stored at: config/guiapi.json
 *
 * Fields:
 *   allow_console_run_with  — whether run_with:console actions are allowed (default: true)
 *   log_unknown_items       — WARN when an unknown item ID is used in a button (default: true)
 *   log_unknown_sounds      — WARN when an unknown sound ID is used in a sound action (default: true)
 */
public final class GuiApiConfig {

    public static final GuiApiConfig INSTANCE = new GuiApiConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH =
            FabricLoader.getInstance().getConfigDir().resolve("guiapi.json");

    // ── Config values ────────────────────────────────────────────────────────

    private boolean allowConsoleRunWith = true;
    private boolean logUnknownItems     = true;
    private boolean logUnknownSounds    = true;
    private int     permissionLevel     = 2;
    private boolean debugMode           = false;

    private GuiApiConfig() {}

    // ── Load / Save ──────────────────────────────────────────────────────────

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            save(); // write defaults
            return;
        }
        try {
            String raw = Files.readString(CONFIG_PATH);
            JsonObject obj = GSON.fromJson(raw, JsonObject.class);
            if (obj == null) { save(); return; }

            if (obj.has("allow_console_run_with"))
                allowConsoleRunWith = obj.get("allow_console_run_with").getAsBoolean();
            if (obj.has("log_unknown_items"))
                logUnknownItems = obj.get("log_unknown_items").getAsBoolean();
            if (obj.has("log_unknown_sounds"))
                logUnknownSounds = obj.get("log_unknown_sounds").getAsBoolean();
            if (obj.has("permission_level"))
                permissionLevel = Math.clamp(obj.get("permission_level").getAsInt(), 0, 4);
            if (obj.has("debug_mode"))
                debugMode = obj.get("debug_mode").getAsBoolean();

        } catch (IOException e) {
            GuiApiMod.LOGGER.error("[GuiAPI] Failed to load config: {}", e.getMessage());
        }
    }

    public void save() {
        JsonObject obj = new JsonObject();
        obj.addProperty("allow_console_run_with", allowConsoleRunWith);
        obj.addProperty("log_unknown_items",       logUnknownItems);
        obj.addProperty("log_unknown_sounds",      logUnknownSounds);
        obj.addProperty("permission_level",        permissionLevel);
        obj.addProperty("debug_mode",              debugMode);
        try {
            Files.writeString(CONFIG_PATH, GSON.toJson(obj));
        } catch (IOException e) {
            GuiApiMod.LOGGER.error("[GuiAPI] Failed to save config: {}", e.getMessage());
        }
    }

    // ── Getters / Setters ────────────────────────────────────────────────────

    public boolean isAllowConsoleRunWith() { return allowConsoleRunWith; }
    public boolean isLogUnknownItems()     { return logUnknownItems; }
    public boolean isLogUnknownSounds()    { return logUnknownSounds; }
    public int     getPermissionLevel()    { return permissionLevel; }

    public void setAllowConsoleRunWith(boolean v) { allowConsoleRunWith = v; }
    public void setLogUnknownItems(boolean v)     { logUnknownItems = v; }
    public void setLogUnknownSounds(boolean v)    { logUnknownSounds = v; }
    public void setPermissionLevel(int v)         { permissionLevel = Math.clamp(v, 0, 4); }

    public boolean isDebugMode()                  { return debugMode; }
    public void setDebugMode(boolean v)           { debugMode = v; }
}
