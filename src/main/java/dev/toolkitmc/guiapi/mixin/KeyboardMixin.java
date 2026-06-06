package dev.toolkitmc.guiapi.mixin;

import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyInject(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (action != 1) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.currentScreen != null) return;

        int configuredKey = dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getOpenGuiKey();
        if (key == configuredKey) {
            client.execute(() -> client.setScreen(
                dev.toolkitmc.guiapi.screen.GuiApiScreen.create(client)
            ));
        }
    }
}
