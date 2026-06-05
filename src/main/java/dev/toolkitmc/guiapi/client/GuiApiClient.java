package dev.toolkitmc.guiapi.client;

import dev.toolkitmc.guiapi.gui.GuiScreenHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public class GuiApiClient implements ClientModInitializer {

    public static KeyBinding openMenuKey;
    
    // Client Feature 2: High-Performance Search Bar & Highlights
    public static boolean isSearchActive = false;
    public static String searchQuery = "";

    @Override
    public void onInitializeClient() {
        // Client Feature 1: Custom Client-Side Keybind (Defaults to G)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.guiapi.open_menu",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_G,
                "category.guiapi.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.wasPressed()) {
                if (client.player != null) {
                    client.player.networkHandler.sendCommand("guiapi open example:welcome");
                }
            }
        });

        // Client Feature 2 & 3: Interactive Slot Search Overlay & Tooltip Styling
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof HandledScreen<?> handledScreen) {
                // If it is our custom chest GUI
                if (handledScreen.getScreenHandler() instanceof GuiScreenHandler) {
                    
                    // Register keypress listener
                    ScreenKeyboardEvents.allowKeyPress(screen).register((screen1, key, scancode, modifiers) -> {
                        // Ctrl + F toggles search
                        if (key == 70 && (modifiers & 2) != 0) { // GLFW_KEY_F = 70, GLFW_MOD_CONTROL = 2
                            isSearchActive = !isSearchActive;
                            if (!isSearchActive) searchQuery = "";
                            return false; // consume key
                        }

                        if (isSearchActive) {
                            if (key == 259) { // GLFW_KEY_BACKSPACE = 259
                                if (!searchQuery.isEmpty()) {
                                    searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                                }
                                return false;
                            } else if (key == 256) { // GLFW_KEY_ESCAPE = 256
                                isSearchActive = false;
                                searchQuery = "";
                                return true;
                            }
                        }
                        return true;
                    });

                    // Register character typed listener for search box input
                    ScreenKeyboardEvents.allowCharTyped(screen).register((screen1, chr, modifiers) -> {
                        if (isSearchActive) {
                            // Only append printable ASCII characters
                            if (chr >= 32 && chr <= 126) {
                                searchQuery += chr;
                            }
                            return false; // consume key
                        }
                        return true;
                    });

                    // Register screen render event
                    ScreenEvents.afterRender(screen).register((screen1, drawContext, mouseX, mouseY, tickCounter) -> {
                        if (isSearchActive) {
                            // Client Feature 3: Smooth Rainbow Hover Glow & Search HUD Box
                            long time = System.currentTimeMillis();
                            int rainbowColor = java.awt.Color.HSBtoRGB((time % 2000) / 2000f, 0.8f, 0.8f);

                            // Draw a beautiful glowing neon search bar overlay at the top left
                            drawContext.fill(10, 10, 180, 26, 0x99000000); // black background
                            drawContext.fill(10, 25, 180, 26, rainbowColor); // glowing bottom border
                            drawContext.drawTextWithShadow(client.textRenderer, "§eSearch: §f" + searchQuery + "§a|", 15, 14, 0xFFFFFF);

                            // Retrieve private screen coordinates safely via reflection
                            int guiLeft = getFieldSafe(handledScreen, "x");
                            int guiTop = getFieldSafe(handledScreen, "y");

                            // Loop through all slots and highlight matches
                            for (Slot slot : handledScreen.getScreenHandler().slots) {
                                ItemStack stack = slot.getStack();
                                if (stack.isEmpty()) continue;

                                boolean isMatch = searchQuery.isEmpty() ||
                                        stack.getName().getString().toLowerCase().contains(searchQuery.toLowerCase()) ||
                                        stack.getTooltip(net.minecraft.item.Item.TooltipContext.DEFAULT, client.player, net.minecraft.item.tooltip.TooltipType.BASIC)
                                                .stream().anyMatch(t -> t.getString().toLowerCase().contains(searchQuery.toLowerCase()));

                                int x = guiLeft + slot.x;
                                int y = guiTop + slot.y;

                                if (isMatch && !searchQuery.isEmpty()) {
                                    // Highlighting slot with a gorgeous glowing green fill
                                    drawContext.fill(x, y, x + 16, y + 16, 0x4400FF00);
                                } else if (!searchQuery.isEmpty()) {
                                    // Dim non-matching slot
                                    drawContext.fill(x, y, x + 16, y + 16, 0xBB000000);
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    private static int getFieldSafe(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getField(fieldName);
            field.setAccessible(true);
            return field.getInt(obj);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.getInt(obj);
            } catch (Exception ex) {
                return 0;
            }
        }
    }
}
