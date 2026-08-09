package dev.toolkitmc.guiapi.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKeyPress(long window, int action, KeyEvent keyEvent, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.gui.screen() == null && action == 1) {
            if (dev.toolkitmc.guiapi.client.GuiApiClient.openMenuKey != null &&
                dev.toolkitmc.guiapi.client.GuiApiClient.openMenuKey.matches(keyEvent)) {
                client.player.connection.sendCommand("guiapi open example:welcome");
            }
        }
    }
}
