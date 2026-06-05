package dev.toolkitmc.guiapi.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.toolkitmc.guiapi.config.GuiApiConfig;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Mod Menu integration — settings screen + loaded GUI list.
 * Only loaded when Mod Menu is present (modCompileOnly dependency).
 */
public class GuiApiModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GuiApiConfigScreen::new;
    }

    // ── Config screen ────────────────────────────────────────────────────────

    static class GuiApiConfigScreen extends Screen {

        private final Screen parent;

        // Live copies of settings (applied on Save)
        private boolean allowConsoleRunWith;
        private boolean logUnknownItems;
        private boolean logUnknownSounds;
        private int     permissionLevel;
        private boolean debugMode;
        private boolean allowCloseOnMove;
        private boolean allowDelayedActions;

        GuiApiConfigScreen(Screen parent) {
            super(Text.literal("GUI API — Settings"));
            this.parent = parent;
            GuiApiConfig cfg = GuiApiConfig.INSTANCE;
            this.allowConsoleRunWith = cfg.isAllowConsoleRunWith();
            this.logUnknownItems     = cfg.isLogUnknownItems();
            this.logUnknownSounds    = cfg.isLogUnknownSounds();
            this.permissionLevel     = cfg.getPermissionLevel();
            this.debugMode           = cfg.isDebugMode();
            this.allowCloseOnMove    = cfg.isAllowCloseOnMove();
            this.allowDelayedActions = cfg.isAllowDelayedActions();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y  = 40;

            // ── Settings ─────────────────────────────────────────────────────

            addToggle(cx, y, "allow_console_run_with",
                    "Allow run_with: console",
                    "Permit buttons to run commands with console (OP-level) permission.",
                    allowConsoleRunWith,
                    v -> allowConsoleRunWith = v);
            y += 24;

            addToggle(cx, y, "log_unknown_items",
                    "Log unknown item IDs",
                    "Print a WARN to the log when a button uses an unrecognized item ID.",
                    logUnknownItems,
                    v -> logUnknownItems = v);
            y += 24;

            addToggle(cx, y, "log_unknown_sounds",
                    "Log unknown sound IDs",
                    "Print a WARN to the log when a sound action uses an unrecognized sound ID.",
                    logUnknownSounds,
                    v -> logUnknownSounds = v);
            y += 24;

            addToggle(cx, y, "debug_mode",
                    "Debug mode",
                    "Log GUI open/close, action execution and placeholder resolution to console.",
                    debugMode,
                    v -> debugMode = v);
            y += 24;

            addToggle(cx, y, "allow_close_on_move",
                    "Allow close_on_move",
                    "Globally permit menus to close automatically when players walk away.",
                    allowCloseOnMove,
                    v -> allowCloseOnMove = v);
            y += 24;

            addToggle(cx, y, "allow_delayed_actions",
                    "Allow action delays",
                    "Globally permit action chains to execute with tick delays.",
                    allowDelayedActions,
                    v -> allowDelayedActions = v);
            y += 24;

            // Permission level — cycle 0-4
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10,
                    Text.literal("§fCommand permission level"), textRenderer));
            addDrawableChild(ButtonWidget.builder(permLevelText(permissionLevel), btn -> {
                permissionLevel = (permissionLevel + 1) % 5;
                btn.setMessage(permLevelText(permissionLevel));
            }).dimensions(cx + 60, y, 40, 20).build());
            y += 30;

            // ── Loaded GUI list ───────────────────────────────────────────────
            var all = GuiRegistry.INSTANCE.getAll();
            int count = all.size();
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                    Text.literal("§7Loaded GUIs: §f" + count +
                            (count == 0 ? " §c(join a world to load datapacks)" : "")),
                    textRenderer));
            y += 12;

            int shown = 0;
            for (var entry : all.entrySet()) {
                if (shown >= 4) {
                    addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                            Text.literal("§8... and " + (count - 4) + " more"), textRenderer));
                    break;
                }
                var id = entry.getKey();
                var def = entry.getValue();

                // Client Feature: Clickable GUI list entries to open GUI Editor Screen
                addDrawableChild(ButtonWidget.builder(
                        Text.literal("Edit: " + id.getPath() + " (" + def.getRows() + " rows)"),
                        btn -> MinecraftClient.getInstance().setScreen(new GuiEditorScreen(this, id, def))
                ).dimensions(cx - 150, y, 300, 18).build());

                y += 20;
                shown++;
            }

            // ── Buttons ───────────────────────────────────────────────────────
            addDrawableChild(ButtonWidget.builder(Text.literal("Save & Close"), btn -> {
                GuiApiConfig cfg = GuiApiConfig.INSTANCE;
                cfg.setAllowConsoleRunWith(allowConsoleRunWith);
                cfg.setLogUnknownItems(logUnknownItems);
                cfg.setLogUnknownSounds(logUnknownSounds);
                cfg.setPermissionLevel(permissionLevel);
                cfg.setDebugMode(debugMode);
                cfg.setAllowCloseOnMove(allowCloseOnMove);
                cfg.setAllowDelayedActions(allowDelayedActions);
                cfg.save();
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 25, 100, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Reload GUIs"), btn -> {
                var client = MinecraftClient.getInstance();
                if (client.player != null) {
                    // Send /guiapi reload as a chat command — works in-game only.
                    client.player.networkHandler.sendChatCommand("guiapi reload");
                    client.setScreen(parent);
                } else {
                    btn.setMessage(Text.literal("§cNot in-game"));
                }
            }).dimensions(cx - 0, height - 25, 100, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                    MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(cx + 105, height - 25, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            // Title
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6GUI API §7Settings"), width / 2, 10, 0xFFFFFF);
            // Divider above buttons
            ctx.fill(width / 2 - 150, height - 32, width / 2 + 150, height - 31, 0x44FFFFFF);
        }

        @Override
        public void close() {
            MinecraftClient.getInstance().setScreen(parent);
        }

        // ── Toggle helper ─────────────────────────────────────────────────────

        private void addToggle(int cx, int y, String key, String label, String tooltip,
                               boolean initial, java.util.function.Consumer<Boolean> onChange) {
            // Label
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10,
                    Text.literal("§f" + label), textRenderer));

            // Toggle button — shows ON/OFF, cycles on click
            ButtonWidget[] ref = new ButtonWidget[1];
            ref[0] = ButtonWidget.builder(toggleText(initial), btn -> {
                boolean next = !btn.getMessage().getString().contains("ON");
                onChange.accept(next);
                btn.setMessage(toggleText(next));
            }).dimensions(cx + 60, y, 40, 20).build();

            addDrawableChild(ref[0]);
        }

        private static Text toggleText(boolean on) {
            return on ? Text.literal("§aON") : Text.literal("§cOFF");
        }

        private static Text permLevelText(int level) {
            String color = switch (level) {
                case 0 -> "§a";
                case 1 -> "§b";
                case 2 -> "§e";
                case 3 -> "§6";
                case 4 -> "§c";
                default -> "§f";
            };
            return Text.literal(color + level);
        }
    }

    // ── GUI Editor Screen ────────────────────────────────────────────────────

    static class GuiEditorScreen extends Screen {
        private final Screen parent;
        private final net.minecraft.util.Identifier id;
        private final GuiDefinition def;

        private TextFieldWidget titleField;
        private int rows;
        private int tickRate;
        private boolean closeOnMove;

        GuiEditorScreen(Screen parent, net.minecraft.util.Identifier id, GuiDefinition def) {
            super(Text.literal("Edit GUI — " + id.getPath()));
            this.parent = parent;
            this.id = id;
            this.def = def;
            this.rows = def.getRows();
            this.tickRate = def.getTickRate();
            this.closeOnMove = def.isCloseOnMove();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 40;

            // Title Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eEdit GUI Title"), textRenderer));
            y += 14;
            titleField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 20, Text.literal("Title"));
            titleField.setMaxLength(128);
            titleField.setText(def.getTitle());
            titleField.setFocused(true);
            addDrawableChild(titleField);
            y += 30;

            // Rows Configuration (Button to Cycle rows 1 to 6)
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fGUI Rows"), textRenderer));
            ButtonWidget[] rowsBtnRef = new ButtonWidget[1];
            rowsBtnRef[0] = ButtonWidget.builder(Text.literal("§a" + rows), btn -> {
                rows = (rows % 6) + 1;
                btn.setMessage(Text.literal("§a" + rows));
            }).dimensions(cx + 60, y, 40, 18).build();
            addDrawableChild(rowsBtnRef[0]);
            y += 24;

            // Close on Move Toggle
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fClose on Move"), textRenderer));
            ButtonWidget[] closeOnMoveBtnRef = new ButtonWidget[1];
            closeOnMoveBtnRef[0] = ButtonWidget.builder(toggleText(closeOnMove), btn -> {
                closeOnMove = !closeOnMove;
                btn.setMessage(toggleText(closeOnMove));
            }).dimensions(cx + 60, y, 40, 18).build();
            addDrawableChild(closeOnMoveBtnRef[0]);
            y += 24;

            // Tick Rate Controls (Increment / Decrement Buttons)
            addDrawableChild(new TextWidget(cx - 150, y + 4, 150, 10, Text.literal("§fTick Rate (Auto-Refresh)"), textRenderer));

            // Display value widget
            TextWidget[] tickRateTextRef = new TextWidget[1];
            tickRateTextRef[0] = new TextWidget(cx + 10, y + 4, 40, 10, Text.literal("§e" + tickRate), textRenderer);
            addDrawableChild(tickRateTextRef[0]);

            // Decrement -5
            addDrawableChild(ButtonWidget.builder(Text.literal("-5"), btn -> {
                tickRate = Math.max(0, tickRate - 5);
                tickRateTextRef[0].setMessage(Text.literal("§e" + tickRate));
            }).dimensions(cx + 50, y, 20, 18).build());

            // Increment +5
            addDrawableChild(ButtonWidget.builder(Text.literal("+5"), btn -> {
                tickRate = Math.min(2400, tickRate + 5);
                tickRateTextRef[0].setMessage(Text.literal("§e" + tickRate));
            }).dimensions(cx + 75, y, 20, 18).build());
            y += 40;

            // Action Buttons
            // Save & Back
            addDrawableChild(ButtonWidget.builder(Text.literal("Apply & Back"), btn -> {
                GuiDefinition newDef = new GuiDefinition(
                        def.getId(),
                        titleField.getText(),
                        rows,
                        def.getButtons(),
                        def.getOnOpen(),
                        def.getOnClose(),
                        def.getFiller(),
                        tickRate,
                        closeOnMove
                );
                dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE.put(id, newDef);
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 25, 100, 20).build());

            // Cancel
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                    MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(cx + 5, height - 25, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6GUI Editor §7— " + id.toString()), width / 2, 10, 0xFFFFFF);
            ctx.fill(width / 2 - 150, height - 32, width / 2 + 150, height - 31, 0x44FFFFFF);
        }

        @Override
        public void tick() {
            titleField.tick();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (titleField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (titleField.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        private static Text toggleText(boolean on) {
            return on ? Text.literal("§aON") : Text.literal("§cOFF");
        }
    }
}
