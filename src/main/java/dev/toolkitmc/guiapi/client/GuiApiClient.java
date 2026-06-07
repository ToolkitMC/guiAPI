package dev.toolkitmc.guiapi.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

public class GuiApiClient implements ClientModInitializer {

    public static KeyBinding openMenuKey;

    @Override
    public void onInitializeClient() {
        // Register Open Menu Key (Defaults to G)
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.guiapi.open_menu",
                InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_G,
                "category.guiapi.general"
        ));
    }
}
