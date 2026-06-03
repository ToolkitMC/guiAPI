package dev.toolkitmc.guiapi.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.toolkitmc.guiapi.config.GuiApiConfig;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.text.Text;

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

        GuiApiConfigScreen(Screen parent) {
            super(Text.literal("GUI API — Settings"));
            this.parent = parent;
            GuiApiConfig cfg = GuiApiConfig.INSTANCE;
            this.allowConsoleRunWith = cfg.isAllowConsoleRunWith();
            this.logUnknownItems     = cfg.isLogUnknownItems();
            this.logUnknownSounds    = cfg.isLogUnknownSounds();
            this.permissionLevel     = cfg.getPermissionLevel();
            this.debugMode           = cfg.isDebugMode();
        }

        @Override
        protected void init() {
            int cx = width / 2;
            int y  = 45;

            // ── Settings ─────────────────────────────────────────────────────

            addToggle(cx, y, "allow_console_run_with",
                    "Allow run_with: console",
                    "Permit buttons to run commands with console (OP-level) permission.",
                    allowConsoleRunWith,
                    v -> allowConsoleRunWith = v);
            y += 28;

            addToggle(cx, y, "log_unknown_items",
                    "Log unknown item IDs",
                    "Print a WARN to the log when a button uses an unrecognized item ID.",
                    logUnknownItems,
                    v -> logUnknownItems = v);
            y += 28;

            addToggle(cx, y, "log_unknown_sounds",
                    "Log unknown sound IDs",
                    "Print a WARN to the log when a sound action uses an unrecognized sound ID.",
                    logUnknownSounds,
                    v -> logUnknownSounds = v);
            y += 28;

            addToggle(cx, y, "debug_mode",
                    "Debug mode",
                    "Log GUI open/close, action execution and placeholder resolution to console.",
                    debugMode,
                    v -> debugMode = v);
            y += 28;

            // Permission level — cycle 0-4
            addDrawableChild(new TextWidget(cx - 150, y + 4, 200, 10,
                    Text.literal("§fCommand permission level"), textRenderer));
            addDrawableChild(ButtonWidget.builder(permLevelText(permissionLevel), btn -> {
                permissionLevel = (permissionLevel + 1) % 5;
                btn.setMessage(permLevelText(permissionLevel));
            }).dimensions(cx + 60, y, 40, 20).build());
            y += 40;

            // ── Loaded GUI list ───────────────────────────────────────────────
            var all = GuiRegistry.INSTANCE.getAll();
            int count = all.size();
            addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                    Text.literal("§7Loaded GUIs: §f" + count +
                            (count == 0 ? " §c(join a world to load datapacks)" : "")),
                    textRenderer));
            y += 14;

            int shown = 0;
            for (var entry : all.entrySet()) {
                if (shown >= 8) {
                    addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                            Text.literal("§8... and " + (count - 8) + " more"), textRenderer));
                    break;
                }
                var def = entry.getValue();
                addDrawableChild(new TextWidget(cx - 150, y, 300, 10,
                        Text.literal("§a" + entry.getKey() +
                                " §8[rows=" + def.getRows() + ", pages=" + def.getPageCount() + "]"),
                        textRenderer));
                y += 12;
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
                cfg.save();
                MinecraftClient.getInstance().setScreen(parent);
            }).dimensions(cx - 105, height - 30, 100, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Reload GUIs"), btn -> {
                var client = MinecraftClient.getInstance();
                if (client.player != null) {
                    // Send /guiapi reload as a chat command — works in-game only.
                    client.player.networkHandler.sendChatCommand("guiapi reload");
                    client.setScreen(parent);
                } else {
                    btn.setMessage(Text.literal("§cNot in-game"));
                }
            }).dimensions(cx - 0, height - 30, 100, 20).build());

            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), btn ->
                    MinecraftClient.getInstance().setScreen(parent))
                    .dimensions(cx + 105, height - 30, 100, 20).build());
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            // Title
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§6GUI API §7Settings"), width / 2, 15, 0xFFFFFF);
            // Divider above buttons
            ctx.fill(width / 2 - 150, height - 40, width / 2 + 150, height - 39, 0x44FFFFFF);
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
}
