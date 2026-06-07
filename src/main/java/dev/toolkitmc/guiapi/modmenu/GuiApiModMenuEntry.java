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
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Mod Menu integration — settings screen + loaded GUI list.
 * Only loaded when Mod Menu is present (modCompileOnly dependency).
 */
public class GuiApiModMenuEntry implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return GuiApiConfigScreen::new;
    }

    // ── Helper methods for Actions string parsing & serialization ────────────

    public static GuiDefinition.ButtonAction parseActionFromString(String str) {
        return GuiActionParser.parseActionFromString(str);
    }

    public static String serializeActionsToString(List<GuiDefinition.ButtonAction> actions) {
        return GuiActionParser.serializeActionsToString(actions);
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
        private boolean allowStatusEffects;
        private boolean logCommands;
        private int     defaultTickRate;

        private int guiListPage = 0;

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
            this.allowStatusEffects  = cfg.isAllowStatusEffects();
            this.logCommands         = cfg.isLogCommands();
            this.defaultTickRate     = cfg.getDefaultTickRate();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y  = 30;

            // ── Settings ─────────────────────────────────────────────────────

            addToggle(cx, y, "allow_console_run_with",
                    "Allow run_with: console",
                    "Permit buttons to run commands with console (OP-level) permission.",
                    allowConsoleRunWith,
                    v -> allowConsoleRunWith = v);
            y += 22;

            addToggle(cx, y, "log_unknown_items",
                    "Log unknown item IDs",
                    "Print a WARN to the log when a button uses an unrecognized item ID.",
                    logUnknownItems,
                    v -> logUnknownItems = v);
            y += 22;

            addToggle(cx, y, "log_unknown_sounds",
                    "Log unknown sound IDs",
                    "Print a WARN to the log when a sound action uses an unrecognized sound ID.",
                    logUnknownSounds,
                    v -> logUnknownSounds = v);
            y += 22;

            addToggle(cx, y, "debug_mode",
                    "Debug mode",
                    "Log GUI open/close, action execution and placeholder resolution to console.",
                    debugMode,
                    v -> debugMode = v);
            y += 22;

            addToggle(cx, y, "allow_close_on_move",
                    "Allow close_on_move",
                    "Globally permit menus to close automatically when players walk away.",
                    allowCloseOnMove,
                    v -> allowCloseOnMove = v);
            y += 22;

            addToggle(cx, y, "allow_delayed_actions",
                    "Allow action delays",
                    "Globally permit action chains to execute with tick delays.",
                    allowDelayedActions,
                    v -> allowDelayedActions = v);
            y += 22;

            addToggle(cx, y, "allow_status_effects",
                    "Allow status effects",
                    "Globally permit buttons and click actions to manage player potion effects.",
                    allowStatusEffects,
                    v -> allowStatusEffects = v);
            y += 22;

            addToggle(cx, y, "log_commands",
                    "Log command executions",
                    "Write a message to log console every time a GUI button runs a command.",
                    logCommands,
                    v -> logCommands = v);
            y += 22;

            // Default Tick Rate Controls
            addDrawableChild(new TextWidget(cx - 150, y + 4, 150, 10, Text.literal("§fDefault Tick Rate"), textRenderer));
            TextWidget[] defaultTickRateTextRef = new TextWidget[1];
            defaultTickRateTextRef[0] = new TextWidget(cx + 10, y + 4, 40, 10, Text.literal("§e" + defaultTickRate), textRenderer);
            addDrawableChild(defaultTickRateTextRef[0]);

            addDrawableChild(ButtonWidget.builder(Text.literal("-5"), btn -> {
                defaultTickRate = Math.max(0, defaultTickRate - 5);
                defaultTickRateTextRef[0].setMessage(Text.literal("§e" + defaultTickRate));
            }).dimensions(cx + 50, y, 20, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("+5"), btn -> {
                defaultTickRate = Math.min(2400, defaultTickRate + 5);
                defaultTickRateTextRef[0].setMessage(Text.literal("§e" + defaultTickRate));
            }).dimensions(cx + 75, y, 20, 18).build());
            y += 22;

            // Permission level — cycle 0-4
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10,
                    Text.literal("§fCommand permission level"), textRenderer));
            addDrawableChild(ButtonWidget.builder(permLevelText(permissionLevel), btn -> {
                permissionLevel = (permissionLevel + 1) % 5;
                btn.setMessage(permLevelText(permissionLevel));
            }).dimensions(cx + 60, y, 40, 20).build());
            y += 26;

            // ── Loaded GUI list (Completed Scrollable/Paginated GUI List) ─────
            var all = GuiRegistry.INSTANCE.getAll();
            int count = all.size();

            int guisPerPage = 3;
            final int totalPages = Math.max(1, (count + guisPerPage - 1) / guisPerPage);
            if (guiListPage >= totalPages) guiListPage = totalPages - 1;

            addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                    Text.literal("§7Loaded GUIs: §f" + count + " (Page " + (guiListPage + 1) + "/" + totalPages + ")"),
                    textRenderer));
            y += 12;

            List<java.util.Map.Entry<net.minecraft.util.Identifier, GuiDefinition>> list = new ArrayList<>(all.entrySet());
            int startIdx = guiListPage * guisPerPage;
            int endIdx = Math.min(startIdx + guisPerPage, count);

            for (int i = startIdx; i < endIdx; i++) {
                var entry = list.get(i);
                var id = entry.getKey();
                var def = entry.getValue();

                // Client Feature: Clickable GUI list entries to open GUI Editor Screen
                addDrawableChild(ButtonWidget.builder(
                        Text.literal("Edit: " + id.getPath() + " (" + def.getRows() + " rows)"),
                        btn -> MinecraftClient.getInstance().setScreen(new GuiEditorScreen(this, id, def))
                ).dimensions(cx - 150, y, 300, 18).build());

                y += 20;
            }

            // Pagination Controls for Loaded GUIs list
            if (totalPages > 1) {
                ButtonWidget prevBtn = ButtonWidget.builder(Text.literal("§e← Prev"), btn -> {
                    guiListPage = Math.max(0, guiListPage - 1);
                    this.init(); // Re-initialize list view
                }).dimensions(cx - 150, y, 145, 18).build();
                prevBtn.active = (guiListPage > 0);
                addDrawableChild(prevBtn);

                ButtonWidget nextBtn = ButtonWidget.builder(Text.literal("§eNext →"), btn -> {
                    guiListPage = Math.min(totalPages - 1, guiListPage + 1);
                    this.init(); // Re-initialize list view
                }).dimensions(cx + 5, y, 145, 18).build();
                nextBtn.active = (guiListPage < totalPages - 1);
                addDrawableChild(nextBtn);
                y += 22;
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
                cfg.setAllowStatusEffects(allowStatusEffects);
                cfg.setLogCommands(logCommands);
                cfg.setDefaultTickRate(defaultTickRate);
                cfg.save();
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 25, 100, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Reload GUIs"), btn -> {
                var client = MinecraftClient.getInstance();
                if (client.player != null) {
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
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10,
                    Text.literal("§f" + label), textRenderer));

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
        private List<GuiDefinition.Button> buttons;
        private Optional<GuiDefinition.FillerConfig> filler;

        GuiEditorScreen(Screen parent, net.minecraft.util.Identifier id, GuiDefinition def) {
            super(Text.literal("Edit GUI — " + id.getPath()));
            this.parent = parent;
            this.id = id;
            this.def = def;
            this.rows = def.getRows();
            this.tickRate = def.getTickRate();
            this.closeOnMove = def.isCloseOnMove();
            this.buttons = new ArrayList<>(def.getButtons());
            this.filler = def.getFiller();
        }

        public void updateButtons(List<GuiDefinition.Button> newButtons) {
            this.buttons = new ArrayList<>(newButtons);
        }

        public void updateFiller(Optional<GuiDefinition.FillerConfig> newFiller) {
            this.filler = newFiller;
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 35;

            // Title Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eEdit GUI Title"), textRenderer));
            y += 12;
            titleField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Title"));
            titleField.setMaxLength(128);
            titleField.setText(def.getTitle());
            titleField.setFocused(true);
            addDrawableChild(titleField);
            y += 24;

            // Rows Configuration (Button to Cycle rows 1 to 6)
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fGUI Rows"), textRenderer));
            ButtonWidget[] rowsBtnRef = new ButtonWidget[1];
            rowsBtnRef[0] = ButtonWidget.builder(Text.literal("§a" + rows), btn -> {
                rows = (rows % 6) + 1;
                btn.setMessage(Text.literal("§a" + rows));
            }).dimensions(cx + 60, y, 40, 18).build();
            addDrawableChild(rowsBtnRef[0]);
            y += 22;

            // Close on Move Toggle
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fClose on Move"), textRenderer));
            ButtonWidget[] closeOnMoveBtnRef = new ButtonWidget[1];
            closeOnMoveBtnRef[0] = ButtonWidget.builder(toggleText(closeOnMove), btn -> {
                closeOnMove = !closeOnMove;
                btn.setMessage(toggleText(closeOnMove));
            }).dimensions(cx + 60, y, 40, 18).build();
            addDrawableChild(closeOnMoveBtnRef[0]);
            y += 22;

            // Tick Rate Controls
            addDrawableChild(new TextWidget(cx - 150, y + 4, 150, 10, Text.literal("§fTick Rate (Auto-Refresh)"), textRenderer));
            TextWidget[] tickRateTextRef = new TextWidget[1];
            tickRateTextRef[0] = new TextWidget(cx + 10, y + 4, 40, 10, Text.literal("§e" + tickRate), textRenderer);
            addDrawableChild(tickRateTextRef[0]);

            addDrawableChild(ButtonWidget.builder(Text.literal("-5"), btn -> {
                tickRate = Math.max(0, tickRate - 5);
                tickRateTextRef[0].setMessage(Text.literal("§e" + tickRate));
            }).dimensions(cx + 50, y, 20, 18).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("+5"), btn -> {
                tickRate = Math.min(2400, tickRate + 5);
                tickRateTextRef[0].setMessage(Text.literal("§e" + tickRate));
            }).dimensions(cx + 75, y, 20, 18).build());
            y += 24;

            // Edit Background Filler
            addDrawableChild(ButtonWidget.builder(Text.literal("§dEdit Background Filler"), btn ->
                    MinecraftClient.getInstance().setScreen(new FillerEditorScreen(this, id, filler))
            ).dimensions(cx - 150, y, 300, 18).build());
            y += 22;

            // Edit Buttons Navigation
            addDrawableChild(ButtonWidget.builder(Text.literal("§bEdit GUI Buttons"), btn ->
                    MinecraftClient.getInstance().setScreen(new ButtonListScreen(this, id, def))
            ).dimensions(cx - 150, y, 300, 18).build());
            y += 28;

            // Action Buttons
            // Save & Back — Open loading screen to search datapack and write JSON directly
            addDrawableChild(ButtonWidget.builder(Text.literal("Apply & Back"), btn -> {
                GuiDefinition newDef = GuiDefinition.create(
                        def.getId(),
                        titleField.getText(),
                        rows,
                        buttons,
                        def.getOnOpen(),
                        def.getOnClose(),
                        filler,
                        tickRate,
                        closeOnMove
                );
                MinecraftClient.getInstance().setScreen(new GuiSaveLoadingScreen(parent, id, newDef));
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

    // ── GUI Save Loading Screen (Client Feature: Persists Datapack on Disk) ──

    static class GuiSaveLoadingScreen extends Screen {
        private final Screen parent;
        private final net.minecraft.util.Identifier id;
        private final GuiDefinition newDef;
        private int ticksElapsed = 0;

        GuiSaveLoadingScreen(Screen parent, net.minecraft.util.Identifier id, GuiDefinition newDef) {
            super(Text.literal("Saving GUI..."));
            this.parent = parent;
            this.id = id;
            this.newDef = newDef;
        }

        @Override
        protected void init() {
            ticksElapsed = 0;
        }

        @Override
        public void tick() {
            ticksElapsed++;
            if (ticksElapsed == 40) { // After 2 seconds, write to disk and trigger reload
                MinecraftServer server = MinecraftClient.getInstance().getServer();
                if (server != null) {
                    // 1. Update in-memory
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE.put(id, newDef);
                    // 2. Save directly to the datapack JSON file on disk
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE.saveToDisk(server, id, newDef);

                    // 3. Reload datapacks so everything syncs perfectly
                    if (MinecraftClient.getInstance().player != null) {
                        MinecraftClient.getInstance().player.networkHandler.sendChatCommand("guiapi reload");
                    }
                }
            } else if (ticksElapsed >= 55) { // Return to settings screen
                MinecraftClient.getInstance().setScreen(parent);
            }
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);

            int cx = width / 2;
            int cy = height / 2;

            long time = System.currentTimeMillis();
            int rainbowColor = java.awt.Color.HSBtoRGB((time % 1500) / 1500f, 0.8f, 0.8f);

            // Draw a solid professional background
            ctx.fill(0, 0, width, height, 0xDD050505);

            String status = "Locating Datapack Folder...";
            if (ticksElapsed >= 20 && ticksElapsed < 40) {
                status = "Overwriting Datapack JSON File...";
            } else if (ticksElapsed >= 40) {
                status = "Reloading GUI API Resources...";
            }

            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§6★ GUI API Datapack Writer ★"), cx, cy - 40, 0xFFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(status), cx, cy - 10, rainbowColor);

            // Draw progress bar
            int barWidth = 160;
            int progress = Math.min(barWidth, (ticksElapsed * barWidth) / 55);
            ctx.fill(cx - barWidth / 2, cy + 15, cx + barWidth / 2, cy + 19, 0x44FFFFFF);
            ctx.fill(cx - barWidth / 2, cy + 15, cx - barWidth / 2 + progress, cy + 19, rainbowColor);
        }
    }

    // ── Filler Editor Screen ─────────────────────────────────────────────────

    static class FillerEditorScreen extends Screen {
        private final GuiEditorScreen parent;
        private final net.minecraft.util.Identifier id;
        private final Optional<GuiDefinition.FillerConfig> currentFiller;

        private TextFieldWidget itemField;
        private TextFieldWidget nameField;
        private boolean glint;
        private boolean hideTooltip;

        FillerEditorScreen(GuiEditorScreen parent, net.minecraft.util.Identifier id, Optional<GuiDefinition.FillerConfig> currentFiller) {
            super(Text.literal("Edit Background Filler"));
            this.parent = parent;
            this.id = id;
            this.currentFiller = currentFiller;

            GuiDefinition.FillerConfig fill = currentFiller.orElse(
                    new GuiDefinition.FillerConfig("minecraft:gray_stained_glass_pane", " ", false, true)
            );
            this.glint = fill.glint();
            this.hideTooltip = fill.hideTooltip();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 35;

            GuiDefinition.FillerConfig fill = currentFiller.orElse(
                    new GuiDefinition.FillerConfig("minecraft:gray_stained_glass_pane", " ", false, true)
            );

            // Item ID Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eFiller Item ID"), textRenderer));
            y += 12;
            itemField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Item ID"));
            itemField.setMaxLength(256);
            itemField.setText(fill.item());
            addDrawableChild(itemField);
            y += 24;

            // Display Name Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eDisplay Name"), textRenderer));
            y += 12;
            nameField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Display Name"));
            nameField.setMaxLength(128);
            nameField.setText(fill.name());
            addDrawableChild(nameField);
            y += 26;

            // Glint Toggle
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fGlint Effect"), textRenderer));
            ButtonWidget[] glintBtnRef = new ButtonWidget[1];
            glintBtnRef[0] = ButtonWidget.builder(toggleText(glint), btn -> {
                glint = !glint;
                btn.setMessage(toggleText(glint));
            }).dimensions(cx + 60, y, 50, 18).build();
            addDrawableChild(glintBtnRef[0]);
            y += 22;

            // Hide Tooltip Toggle
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10, Text.literal("§fHide Tooltip"), textRenderer));
            ButtonWidget[] hideTooltipBtnRef = new ButtonWidget[1];
            hideTooltipBtnRef[0] = ButtonWidget.builder(toggleText(hideTooltip), btn -> {
                hideTooltip = !hideTooltip;
                btn.setMessage(toggleText(hideTooltip));
            }).dimensions(cx + 60, y, 50, 18).build();
            addDrawableChild(hideTooltipBtnRef[0]);
            y += 35;

            // Save
            addDrawableChild(ButtonWidget.builder(Text.literal("Apply Filler"), btn -> {
                GuiDefinition.FillerConfig newFiller = new GuiDefinition.FillerConfig(
                        itemField.getText(),
                        nameField.getText(),
                        glint,
                        hideTooltip
                );
                parent.updateFiller(Optional.of(newFiller));
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 25, 100, 20).build());

            // Remove Filler completely
            addDrawableChild(ButtonWidget.builder(Text.literal("Disable Filler"), btn -> {
                parent.updateFiller(Optional.empty());
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx + 5, height - 25, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6Edit Background Filler"), width / 2, 10, 0xFFFFFF);
            ctx.fill(width / 2 - 150, height - 32, width / 2 + 150, height - 31, 0x44FFFFFF);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (itemField.keyPressed(keyCode, scanCode, modifiers) ||
                    nameField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (itemField.charTyped(chr, modifiers) ||
                    nameField.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        private static Text toggleText(boolean on) {
            return on ? Text.literal("§aON") : Text.literal("§cOFF");
        }
    }

    // ── Buttons List Screen ──────────────────────────────────────────────────

    static class ButtonListScreen extends Screen {
        private final GuiEditorScreen parent;
        private final net.minecraft.util.Identifier id;
        private final GuiDefinition def;
        private final List<GuiDefinition.Button> buttonsList;

        ButtonListScreen(GuiEditorScreen parent, net.minecraft.util.Identifier id, GuiDefinition def) {
            super(Text.literal("Buttons List"));
            this.parent = parent;
            this.id = id;
            this.def = def;
            this.buttonsList = new ArrayList<>(parent.buttons);
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 35;

            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eSelect a button to edit or delete:"), textRenderer));
            y += 14;

            // List of existing buttons (limit to first 7 to fit, or provide navigation)
            int shown = 0;
            for (int i = 0; i < buttonsList.size(); i++) {
                if (shown >= 7) {
                    addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                            Text.literal("§8... and " + (buttonsList.size() - 7) + " more buttons"), textRenderer));
                    break;
                }
                final int index = i;
                GuiDefinition.Button btn = buttonsList.get(index);
                String labelText = "Slot " + btn.slot() + ": " +
                        (btn.name().isEmpty() ? btn.item() : btn.name());

                addDrawableChild(ButtonWidget.builder(Text.literal(labelText), b -> {
                    MinecraftClient.getInstance().setScreen(new ButtonEditorScreen(this, index, btn));
                }).dimensions(cx - 150, y, 300, 18).build());

                y += 20;
                shown++;
            }

            y = height - 30;

            // Add New Button
            addDrawableChild(ButtonWidget.builder(Text.literal("§aAdd New Button"), btn -> {
                GuiDefinition.Button newBtn = new GuiDefinition.Button(
                        0, 0, "minecraft:stone", "New Button", List.of(), false,
                        GuiDefinition.ClickType.ANY, Optional.empty(), List.of(), Optional.empty(),
                        Optional.empty(), Optional.empty(), "1", false, false
                );
                buttonsList.add(newBtn);
                MinecraftClient.getInstance().setScreen(new ButtonEditorScreen(this, buttonsList.size() - 1, newBtn));
            }).dimensions(cx - 155, y, 100, 20).build());

            // Save & Back
            addDrawableChild(ButtonWidget.builder(Text.literal("Save Buttons"), btn -> {
                // Update parent editor screen copy!
                parent.updateButtons(buttonsList);
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 50, y, 100, 20).build());

            // Cancel
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                    MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(cx + 55, y, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6Buttons Editor — " + id.getPath()), width / 2, 10, 0xFFFFFF);
            ctx.fill(width / 2 - 150, height - 38, width / 2 + 150, height - 37, 0x44FFFFFF);
        }

        public void updateButton(int index, GuiDefinition.Button btn) {
            buttonsList.set(index, btn);
        }

        public void deleteButton(int index) {
            if (index >= 0 && index < buttonsList.size()) {
                buttonsList.remove(index);
            }
        }
    }

    // ── Button Properties Editor Screen ──────────────────────────────────────

    static class ButtonEditorScreen extends Screen {
        private final ButtonListScreen parent;
        private final int index;
        private final GuiDefinition.Button btn;

        private TextFieldWidget slotField;
        private TextFieldWidget itemField;
        private TextFieldWidget nameField;
        private TextFieldWidget amountField;
        private TextFieldWidget loreField; // Combined Lore input field (separated by ;)

        // Combined Actions input field (separated by ;)
        private TextFieldWidget actionsField;

        private boolean glint;
        private Optional<GuiDefinition.ToggleDefinition> toggle;

        ButtonEditorScreen(ButtonListScreen parent, int index, GuiDefinition.Button btn) {
            super(Text.literal("Edit Button"));
            this.parent = parent;
            this.index = index;
            this.btn = btn;
            this.glint = btn.glint();
            this.toggle = btn.toggle();
        }

        public void updateToggle(Optional<GuiDefinition.ToggleDefinition> newToggle) {
            this.toggle = newToggle;
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 25;

            // Slot Input
            addDrawableChild(new TextWidget(cx - 150, y, 100, 10, Text.literal("§eSlot (0-53)"), textRenderer));
            slotField = new TextFieldWidget(textRenderer, cx - 150, y + 12, 60, 18, Text.literal("Slot"));
            slotField.setText(String.valueOf(btn.slot()));
            addDrawableChild(slotField);

            // Amount Input
            addDrawableChild(new TextWidget(cx - 80, y, 100, 10, Text.literal("§eAmount"), textRenderer));
            amountField = new TextFieldWidget(textRenderer, cx - 80, y + 12, 60, 18, Text.literal("Amount"));
            amountField.setText(btn.amount());
            addDrawableChild(amountField);

            // Glint Toggle
            addDrawableChild(new TextWidget(cx - 10, y, 100, 10, Text.literal("§eGlint"), textRenderer));
            ButtonWidget[] glintBtnRef = new ButtonWidget[1];
            glintBtnRef[0] = ButtonWidget.builder(toggleText(glint), b -> {
                glint = !glint;
                b.setMessage(toggleText(glint));
            }).dimensions(cx - 10, y + 12, 40, 18).build();
            addDrawableChild(glintBtnRef[0]);

            // Edit Toggle Properties Navigation
            addDrawableChild(new TextWidget(cx + 40, y, 110, 10, Text.literal("§eToggle Button"), textRenderer));
            ButtonWidget[] toggleBtnRef = new ButtonWidget[1];
            toggleBtnRef[0] = ButtonWidget.builder(Text.literal(toggle.isPresent() ? "§aCONFIGURED" : "§cDISABLED"), b -> {
                MinecraftClient.getInstance().setScreen(new ToggleEditorScreen(this, toggle));
            }).dimensions(cx + 40, y + 12, 110, 18).build();
            addDrawableChild(toggleBtnRef[0]);
            y += 38;

            // Item ID Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eItem ID"), textRenderer));
            y += 12;
            itemField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Item ID"));
            itemField.setMaxLength(256);
            itemField.setText(btn.item());
            addDrawableChild(itemField);
            y += 24;

            // Display Name Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eDisplay Name"), textRenderer));
            y += 12;
            nameField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Display Name"));
            nameField.setMaxLength(128);
            nameField.setText(btn.name());
            addDrawableChild(nameField);
            y += 24;

            // Lore Input (joined by ;)
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eLore Lines (Separate by semicolon ';')"), textRenderer));
            y += 12;
            loreField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Lore"));
            loreField.setMaxLength(512);
            loreField.setText(String.join(";", btn.lore()));
            addDrawableChild(loreField);
            y += 24;

            // Multiple Actions Input (joined by ;)
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eActions (Format: 'type:value' or 'type:varKey:value', separate by ';')"), textRenderer));
            y += 12;
            actionsField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 18, Text.literal("Actions"));
            actionsField.setMaxLength(512);
            actionsField.setText(serializeActionsToString(btn.actions()));
            addDrawableChild(actionsField);
            y += 32;

            // Actions
            // Save/Apply
            addDrawableChild(ButtonWidget.builder(Text.literal("Apply"), b -> {
                int slotVal = 0;
                try {
                    slotVal = Math.max(0, Integer.parseInt(slotField.getText()));
                } catch (NumberFormatException ignored) {}

                // Build Lore list
                List<String> finalLore = new ArrayList<>();
                String loreTxt = loreField.getText();
                if (!loreTxt.isEmpty()) {
                    for (String s : loreTxt.split(";")) {
                        finalLore.add(s);
                    }
                }

                // Build Actions list from semicolon-separated string
                List<GuiDefinition.ButtonAction> finalActions = new ArrayList<>();
                String actionsTxt = actionsField.getText();
                if (!actionsTxt.isEmpty()) {
                    for (String s : actionsTxt.split(";")) {
                        finalActions.add(parseActionFromString(s));
                    }
                }
                if (finalActions.isEmpty()) {
                    finalActions.add(new GuiDefinition.ButtonAction(GuiDefinition.ActionType.CLOSE, ""));
                }

                GuiDefinition.Button newBtn = new GuiDefinition.Button(
                        slotVal,
                        btn.page(),
                        itemField.getText(),
                        nameField.getText(),
                        finalLore,
                        glint,
                        btn.clickType(),
                        btn.condition(),
                        finalActions,
                        toggle,
                        btn.customModelData(),
                        btn.itemModel(),
                        amountField.getText(),
                        btn.hideTooltip(),
                        btn.hideAdditionalTooltip()
                );
                parent.updateButton(index, newBtn);
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 155, height - 25, 100, 20).build());

            // Delete Button
            addDrawableChild(ButtonWidget.builder(Text.literal("§cDelete Button"), b -> {
                parent.deleteButton(index);
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 50, height - 25, 100, 20).build());

            // Cancel
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b ->
                    MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(cx + 55, height - 25, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6Edit Button Properties"), width / 2, 10, 0xFFFFFF);
            ctx.fill(width / 2 - 150, height - 32, width / 2 + 150, height - 31, 0x44FFFFFF);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (slotField.keyPressed(keyCode, scanCode, modifiers) ||
                    amountField.keyPressed(keyCode, scanCode, modifiers) ||
                    itemField.keyPressed(keyCode, scanCode, modifiers) ||
                    nameField.keyPressed(keyCode, scanCode, modifiers) ||
                    loreField.keyPressed(keyCode, scanCode, modifiers) ||
                    actionsField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (slotField.charTyped(chr, modifiers) ||
                    amountField.charTyped(chr, modifiers) ||
                    itemField.charTyped(chr, modifiers) ||
                    nameField.charTyped(chr, modifiers) ||
                    loreField.charTyped(chr, modifiers) ||
                    actionsField.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }

        private static Text toggleText(boolean on) {
            return on ? Text.literal("§aON") : Text.literal("§cOFF");
        }
    }

    // ── Toggle Editor Screen ─────────────────────────────────────────────────

    static class ToggleEditorScreen extends Screen {
        private final ButtonEditorScreen parent;
        private final Optional<GuiDefinition.ToggleDefinition> currentToggle;

        private TextFieldWidget tagField;
        private TextFieldWidget itemOnField;
        private TextFieldWidget itemOffField;
        private TextFieldWidget nameOnField;
        private TextFieldWidget nameOffField;
        private TextFieldWidget actionsOnField;  // Multiple Actions ON (separated by ;)
        private TextFieldWidget actionsOffField; // Multiple Actions OFF (separated by ;)

        ToggleEditorScreen(ButtonEditorScreen parent, Optional<GuiDefinition.ToggleDefinition> currentToggle) {
            super(Text.literal("Edit Toggle Properties"));
            this.parent = parent;
            this.currentToggle = currentToggle;
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y = 30;

            GuiDefinition.ToggleDefinition tgl = currentToggle.orElse(
                    new GuiDefinition.ToggleDefinition(
                            "pvp_enabled", "minecraft:lime_dye", "minecraft:gray_dye",
                            "§aEnabled", "§7Disabled", List.of(), List.of(), false, false,
                            List.of(), List.of(),
                            Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                            "1", "1", false, false, false, false
                    )
            );

            // Tag Input
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eScoreboard Tag"), textRenderer));
            y += 11;
            tagField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 17, Text.literal("Tag"));
            tagField.setText(tgl.tag());
            addDrawableChild(tagField);
            y += 22;

            // Item ON / OFF Inputs
            addDrawableChild(new TextWidget(cx - 150, y, 145, 10, Text.literal("§eItem ON"), textRenderer));
            addDrawableChild(new TextWidget(cx + 5, y, 145, 10, Text.literal("§eItem OFF"), textRenderer));
            y += 11;

            itemOnField = new TextFieldWidget(textRenderer, cx - 150, y, 145, 17, Text.literal("Item ON"));
            itemOnField.setText(tgl.itemOn());
            addDrawableChild(itemOnField);

            itemOffField = new TextFieldWidget(textRenderer, cx + 5, y, 145, 17, Text.literal("Item OFF"));
            itemOffField.setText(tgl.itemOff());
            addDrawableChild(itemOffField);
            y += 22;

            // Name ON / OFF Inputs
            addDrawableChild(new TextWidget(cx - 150, y, 145, 10, Text.literal("§eDisplay Name ON"), textRenderer));
            addDrawableChild(new TextWidget(cx + 5, y, 145, 10, Text.literal("§eDisplay Name OFF"), textRenderer));
            y += 11;

            nameOnField = new TextFieldWidget(textRenderer, cx - 150, y, 145, 17, Text.literal("Name ON"));
            nameOnField.setText(tgl.nameOn());
            addDrawableChild(nameOnField);

            nameOffField = new TextFieldWidget(textRenderer, cx + 5, y, 145, 17, Text.literal("Name OFF"));
            nameOffField.setText(tgl.nameOff());
            addDrawableChild(nameOffField);
            y += 22;

            // Actions ON / OFF Inputs (Separate by ;)
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eActions ON (Separate by ';')"), textRenderer));
            y += 11;
            actionsOnField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 17, Text.literal("Actions ON"));
            actionsOnField.setMaxLength(512);
            actionsOnField.setText(serializeActionsToString(tgl.actionsOn()));
            addDrawableChild(actionsOnField);
            y += 22;

            addDrawableChild(new TextWidget(cx - 150, y, 300, 10, Text.literal("§eActions OFF (Separate by ';')"), textRenderer));
            y += 11;
            actionsOffField = new TextFieldWidget(textRenderer, cx - 150, y, 300, 17, Text.literal("Actions OFF"));
            actionsOffField.setMaxLength(512);
            actionsOffField.setText(serializeActionsToString(tgl.actionsOff()));
            addDrawableChild(actionsOffField);
            y += 26;

            // Save / Apply Toggle properties
            addDrawableChild(ButtonWidget.builder(Text.literal("Apply Toggle"), btn -> {
                // Build Actions ON list
                List<GuiDefinition.ButtonAction> finalOnActions = new ArrayList<>();
                String onActionsTxt = actionsOnField.getText();
                if (!onActionsTxt.isEmpty()) {
                    for (String s : onActionsTxt.split(";")) {
                        finalOnActions.add(parseActionFromString(s));
                    }
                }
                if (finalOnActions.isEmpty()) {
                    finalOnActions.add(new GuiDefinition.ButtonAction(GuiDefinition.ActionType.RUN_COMMAND, "tag @s remove " + tagField.getText(), GuiDefinition.RunWith.CONSOLE));
                }

                // Build Actions OFF list
                List<GuiDefinition.ButtonAction> finalOffActions = new ArrayList<>();
                String offActionsTxt = actionsOffField.getText();
                if (!offActionsTxt.isEmpty()) {
                    for (String s : offActionsTxt.split(";")) {
                        finalOffActions.add(parseActionFromString(s));
                    }
                }
                if (finalOffActions.isEmpty()) {
                    finalOffActions.add(new GuiDefinition.ButtonAction(GuiDefinition.ActionType.RUN_COMMAND, "tag @s add " + tagField.getText(), GuiDefinition.RunWith.CONSOLE));
                }

                GuiDefinition.ToggleDefinition newToggle = new GuiDefinition.ToggleDefinition(
                        tagField.getText(),
                        itemOnField.getText(),
                        itemOffField.getText(),
                        nameOnField.getText(),
                        nameOffField.getText(),
                        tgl.loreOn(),
                        tgl.loreOff(),
                        tgl.glintOn(),
                        tgl.glintOff(),
                        finalOnActions,
                        finalOffActions,
                        tgl.customModelDataOn(),
                        tgl.customModelDataOff(),
                        tgl.itemModelOn(),
                        tgl.itemModelOff(),
                        tgl.amountOn(),
                        tgl.amountOff(),
                        tgl.hideTooltipOn(),
                        tgl.hideTooltipOff(),
                        tgl.hideAdditionalTooltipOn(),
                        tgl.hideAdditionalTooltipOff()
                );
                parent.updateToggle(Optional.of(newToggle));
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 25, 100, 20).build());

            // Disable Toggle Completely
            addDrawableChild(ButtonWidget.builder(Text.literal("Disable Toggle"), btn -> {
                parent.updateToggle(Optional.empty());
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx + 5, height - 25, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6Edit Toggle Properties"), width / 2, 10, 0xFFFFFF);
            ctx.fill(width / 2 - 150, height - 32, width / 2 + 150, height - 31, 0x44FFFFFF);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (tagField.keyPressed(keyCode, scanCode, modifiers) ||
                    itemOnField.keyPressed(keyCode, scanCode, modifiers) ||
                    itemOffField.keyPressed(keyCode, scanCode, modifiers) ||
                    nameOnField.keyPressed(keyCode, scanCode, modifiers) ||
                    nameOffField.keyPressed(keyCode, scanCode, modifiers) ||
                    actionsOnField.keyPressed(keyCode, scanCode, modifiers) ||
                    actionsOffField.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (tagField.charTyped(chr, modifiers) ||
                    itemOnField.charTyped(chr, modifiers) ||
                    itemOffField.charTyped(chr, modifiers) ||
                    nameOnField.charTyped(chr, modifiers) ||
                    nameOffField.charTyped(chr, modifiers) ||
                    actionsOnField.charTyped(chr, modifiers) ||
                    actionsOffField.charTyped(chr, modifiers)) {
                return true;
            }
            return super.charTyped(chr, modifiers);
        }
    }
}
