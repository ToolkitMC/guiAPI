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
        if (client.player != null && client.currentScreen == null && action == 1) {
            // Check if the pressed key matches our official open menu keybinding (Defaults to G!)
            if (dev.toolkitmc.guiapi.client.GuiApiClient.openMenuKey.matchesKey(key, scancode)) {
                client.player.networkHandler.sendChatCommand("guiapi open example:welcome");
            }
        }
    }
}
