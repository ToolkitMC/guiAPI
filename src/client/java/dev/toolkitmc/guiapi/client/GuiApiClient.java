package dev.toolkitmc.guiapi.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class GuiApiClient implements ClientModInitializer {

    public static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        // Register Open Menu Key (Defaults to G) - category guiapi
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.guiapi.open_menu",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_G,
                KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("guiapi", "general"))
        ));
    }
}
