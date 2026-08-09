package dev.toolkitmc.guiapi.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.toolkitmc.guiapi.config.GuiApiConfig;
import dev.toolkitmc.guiapi.gui.GuiActionParser;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mod Menu integration — settings screen for 26.2.
 * Simplified for unobfuscated mappings: uses GuiGraphics, Button.bounds, and Minecraft.gui.setScreen.
 */
public class GuiApiModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> {
            me.shedaniel.clothconfig2.api.ConfigBuilder builder = me.shedaniel.clothconfig2.api.ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("GUI API Settings"));

            me.shedaniel.clothconfig2.api.ConfigCategory generalCategory = builder.getOrCreateCategory(Component.literal("Settings"));
            me.shedaniel.clothconfig2.api.ConfigCategory otherCategory = builder.getOrCreateCategory(Component.literal("Other"));
            me.shedaniel.clothconfig2.api.ConfigCategory guisCategory = builder.getOrCreateCategory(Component.literal("Loaded GUIs"));

            me.shedaniel.clothconfig2.api.ConfigEntryBuilder entryBuilder = builder.entryBuilder();

            GuiApiConfig cfg = GuiApiConfig.INSTANCE;

            // --- CATEGORY 1: Settings ---
            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Allow run_with: console"), cfg.isAllowConsoleRunWith())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setAllowConsoleRunWith)
                .setTooltip(Component.literal("Permit buttons to run commands with console (OP-level) permission."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Log unknown item IDs"), cfg.isLogUnknownItems())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setLogUnknownItems)
                .setTooltip(Component.literal("Print a WARN to the log when a button uses an unrecognized item ID."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Log unknown sound IDs"), cfg.isLogUnknownSounds())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setLogUnknownSounds)
                .setTooltip(Component.literal("Print a WARN to the log when a sound action uses an unrecognized sound ID."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Debug mode"), cfg.isDebugMode())
                .setDefaultValue(false)
                .setSaveConsumer(cfg::setDebugMode)
                .setTooltip(Component.literal("Log GUI open/close, action execution and placeholder resolution to console."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Allow close_on_move"), cfg.isAllowCloseOnMove())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setAllowCloseOnMove)
                .setTooltip(Component.literal("Globally permit menus to close automatically when players walk away."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Allow action delays"), cfg.isAllowDelayedActions())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setAllowDelayedActions)
                .setTooltip(Component.literal("Globally permit action chains to execute with tick delays."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Allow status effects"), cfg.isAllowStatusEffects())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setAllowStatusEffects)
                .setTooltip(Component.literal("Globally permit buttons and click actions to manage player potion effects."))
                .build());

            generalCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Log command executions"), cfg.isLogCommands())
                .setDefaultValue(false)
                .setSaveConsumer(cfg::setLogCommands)
                .setTooltip(Component.literal("Write a message to log console every time a GUI button runs a command."))
                .build());

            generalCategory.addEntry(entryBuilder.startIntSlider(Component.literal("Default Tick Rate"), cfg.getDefaultTickRate(), 1, 100)
                .setDefaultValue(20)
                .setSaveConsumer(cfg::setDefaultTickRate)
                .setTooltip(Component.literal("Default tick rate for auto-refreshing menus."))
                .build());

            generalCategory.addEntry(entryBuilder.startIntSlider(Component.literal("Command permission level"), cfg.getPermissionLevel(), 0, 4)
                .setDefaultValue(2)
                .setSaveConsumer(cfg::setPermissionLevel)
                .setTooltip(Component.literal("Permission level for running standard commands."))
                .build());

            // --- CATEGORY 2: Other ---
            otherCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Enable button glint"), cfg.isEnableButtonGlint())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setEnableButtonGlint)
                .setTooltip(Component.literal("Toggles whether to show the glowing enchantment shine on buttons."))
                .build());

            otherCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Show developer item IDs"), cfg.isShowItemIdsDeveloper())
                .setDefaultValue(false)
                .setSaveConsumer(cfg::setShowItemIdsDeveloper)
                .setTooltip(Component.literal("Show technical item IDs in tooltips for designers."))
                .build());

            otherCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Mute click errors"), cfg.isMuteClickErrors())
                .setDefaultValue(false)
                .setSaveConsumer(cfg::setMuteClickErrors)
                .setTooltip(Component.literal("Silence warn messages when clicks fail to meet conditions or permissions."))
                .build());

            otherCategory.addEntry(entryBuilder.startBooleanToggle(Component.literal("Play close sound"), cfg.isEnableCloseSound())
                .setDefaultValue(true)
                .setSaveConsumer(cfg::setEnableCloseSound)
                .setTooltip(Component.literal("Play a clean chest close sound when closing virtual GUIs."))
                .build());

            otherCategory.addEntry(entryBuilder.startTextField(Component.literal("Chat Prefix"), cfg.getChatPrefix())
                .setDefaultValue("§8[§6GuiAPI§8] §f")
                .setSaveConsumer(cfg::setChatPrefix)
                .setTooltip(Component.literal("Custom prefix for all chat messages sent by GuiAPI."))
                .build());

            otherCategory.addEntry(entryBuilder.startIntSlider(Component.literal("Sound Volume (%)"), cfg.getSoundVolume(), 0, 100)
                .setDefaultValue(100)
                .setSaveConsumer(cfg::setSoundVolume)
                .setTooltip(Component.literal("Global multiplier for mod UI sound volumes."))
                .build());

            otherCategory.addEntry(entryBuilder.startSelector(Component.literal("Message Execute Mode"), new String[]{"CHAT", "SYSTEM", "SILENT"}, cfg.getCommandExecuteMode())
                .setDefaultValue("CHAT")
                .setSaveConsumer(cfg::setCommandExecuteMode)
                .setTooltip(Component.literal("Where button message feedback is routed."))
                .build());

            // --- CATEGORY 3: Loaded GUIs (simplified) ---
            guisCategory.addEntry(entryBuilder.startTextDescription(Component.literal("§eLoaded GUIs: " + dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE.getAll().size() + " found. Use /guiapi list in-game."))
                .build());
            guisCategory.addEntry(entryBuilder.startTextDescription(Component.literal("§7Visual GUI Editor is available via commands in 26.2. Open Mod Menu config to adjust global settings."))
                .build());
            
            builder.setSavingRunnable(() -> {
                cfg.save();
            });

            return builder.build();
        };
    }

    // Helper methods for Actions string parsing & serialization (kept for compatibility)
    public static GuiDefinition.ButtonAction parseActionFromString(String str) {
        return GuiActionParser.parseActionFromString(str);
    }

    public static String serializeActionsToString(java.util.List<GuiDefinition.ButtonAction> actions) {
        return GuiActionParser.serializeActionsToString(actions);
    }

    // Minimal placeholder screens to satisfy references if any external code calls them
    public static class GuiEditorScreen extends Screen {
        protected GuiEditorScreen(Screen parent, net.minecraft.resources.Identifier id, GuiDefinition def) {
            super(Component.literal("GUI Editor (26.2)"));
        }
        @Override
        protected void init() {}
    }
    public static class ButtonEditorScreen extends Screen {
        protected ButtonEditorScreen(Screen parent, GuiDefinition def, int index) {
            super(Component.literal("Button Editor"));
        }
        @Override
        protected void init() {}
    }
    public static class ToggleEditorScreen extends Screen {
        protected ToggleEditorScreen(Screen parent, java.util.Optional<GuiDefinition.ToggleDefinition> tgl) {
            super(Component.literal("Toggle Editor"));
        }
        @Override
        protected void init() {}
    }
}
