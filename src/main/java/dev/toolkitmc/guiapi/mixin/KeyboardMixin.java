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
        MinecraftClient client = MinecraftClient.getInstance();
        // Check if key is GLFW_KEY_G (71) and pressed (action == 1) and no screen is open
        if (client.player != null && client.currentScreen == null && key == 71 && action == 1) {
            client.player.networkHandler.sendChatCommand("guiapi open example:welcome");
        }
    }
}
