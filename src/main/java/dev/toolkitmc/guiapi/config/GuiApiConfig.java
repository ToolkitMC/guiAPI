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
    private boolean allowCloseOnMove    = true;
    private boolean allowDelayedActions = true;
    private boolean allowStatusEffects  = true; // 1. New Config
    private boolean logCommands         = false; // 2. New Config
    private int     defaultTickRate     = 20;    // 3. New Config

    private boolean enableButtonGlint    = true;  // 4. New Config
    private boolean showItemIdsDeveloper = false; // 5. New Config
    private boolean muteClickErrors      = false; // 6. New Config
    private boolean enableCloseSound     = true;  // 7. New Config

    private String  chatPrefix           = "§8[§6GuiAPI§8] §f"; // 8. New Config
    private int     soundVolume          = 100;                 // 9. New Config
    private String  commandExecuteMode   = "CHAT";              // 10. New Config

    private boolean allowGiveItem  = true;  // 11. give_item action guard
    private boolean allowBroadcast = true;  // 12. broadcast action guard

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
            if (obj.has("allow_close_on_move"))
                allowCloseOnMove = obj.get("allow_close_on_move").getAsBoolean();
            if (obj.has("allow_delayed_actions"))
                allowDelayedActions = obj.get("allow_delayed_actions").getAsBoolean();
            if (obj.has("allow_status_effects"))
                allowStatusEffects = obj.get("allow_status_effects").getAsBoolean();
            if (obj.has("log_commands"))
                logCommands = obj.get("log_commands").getAsBoolean();
            if (obj.has("default_tick_rate"))
                defaultTickRate = obj.get("default_tick_rate").getAsInt();
            if (obj.has("enable_button_glint"))
                enableButtonGlint = obj.get("enable_button_glint").getAsBoolean();
            if (obj.has("show_item_ids_developer"))
                showItemIdsDeveloper = obj.get("show_item_ids_developer").getAsBoolean();
            if (obj.has("mute_click_errors"))
                muteClickErrors = obj.get("mute_click_errors").getAsBoolean();
            if (obj.has("enable_close_sound"))
                enableCloseSound = obj.get("enable_close_sound").getAsBoolean();
            if (obj.has("chat_prefix"))
                chatPrefix = obj.get("chat_prefix").getAsString();
            if (obj.has("sound_volume"))
                soundVolume = Math.clamp(obj.get("sound_volume").getAsInt(), 0, 100);
            if (obj.has("command_execute_mode"))
                commandExecuteMode = obj.get("command_execute_mode").getAsString();
            if (obj.has("allow_give_item"))
                allowGiveItem = obj.get("allow_give_item").getAsBoolean();
            if (obj.has("allow_broadcast"))
                allowBroadcast = obj.get("allow_broadcast").getAsBoolean();

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
        obj.addProperty("allow_close_on_move",     allowCloseOnMove);
        obj.addProperty("allow_delayed_actions",   allowDelayedActions);
        obj.addProperty("allow_status_effects",    allowStatusEffects);
        obj.addProperty("log_commands",            logCommands);
        obj.addProperty("default_tick_rate",       defaultTickRate);
        obj.addProperty("enable_button_glint",     enableButtonGlint);
        obj.addProperty("show_item_ids_developer", showItemIdsDeveloper);
        obj.addProperty("mute_click_errors",      muteClickErrors);
        obj.addProperty("enable_close_sound",     enableCloseSound);
        obj.addProperty("chat_prefix",             chatPrefix);
        obj.addProperty("sound_volume",            soundVolume);
        obj.addProperty("command_execute_mode",    commandExecuteMode);
        obj.addProperty("allow_give_item",         allowGiveItem);
        obj.addProperty("allow_broadcast",         allowBroadcast);
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

    public boolean isAllowCloseOnMove()           { return allowCloseOnMove; }
    public void setAllowCloseOnMove(boolean v)    { allowCloseOnMove = v; }

    public boolean isAllowDelayedActions()         { return allowDelayedActions; }
    public void setAllowDelayedActions(boolean v)  { allowDelayedActions = v; }

    public boolean isAllowStatusEffects()         { return allowStatusEffects; }
    public void setAllowStatusEffects(boolean v)  { allowStatusEffects = v; }

    public boolean isLogCommands()                { return logCommands; }
    public void setLogCommands(boolean v)         { logCommands = v; }

    public int getDefaultTickRate()               { return defaultTickRate; }
    public void setDefaultTickRate(int v)         { defaultTickRate = v; }

    public boolean isEnableButtonGlint() { return enableButtonGlint; }
    public void setEnableButtonGlint(boolean v) { enableButtonGlint = v; }

    public boolean isShowItemIdsDeveloper() { return showItemIdsDeveloper; }
    public void setShowItemIdsDeveloper(boolean v) { showItemIdsDeveloper = v; }

    public boolean isMuteClickErrors() { return muteClickErrors; }
    public void setMuteClickErrors(boolean v) { muteClickErrors = v; }

    public boolean isEnableCloseSound() { return enableCloseSound; }
    public void setEnableCloseSound(boolean v) { enableCloseSound = v; }

    public String getChatPrefix() { return chatPrefix; }
    public void setChatPrefix(String v) { chatPrefix = v; }

    public int getSoundVolume() { return soundVolume; }
    public void setSoundVolume(int v) { soundVolume = Math.clamp(v, 0, 100); }

    public String getCommandExecuteMode() { return commandExecuteMode; }
    public void setCommandExecuteMode(String v) { commandExecuteMode = v; }

    public boolean isAllowGiveItem()          { return allowGiveItem; }
    public void setAllowGiveItem(boolean v)   { allowGiveItem = v; }

    public boolean isAllowBroadcast()         { return allowBroadcast; }
    public void setAllowBroadcast(boolean v)  { allowBroadcast = v; }
}
