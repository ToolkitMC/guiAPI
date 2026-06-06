package dev.toolkitmc.guiapi.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.GenericContainerScreenHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void charTypedInject(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        Screen screen = (Screen)(Object)this;
        if (screen instanceof HandledScreen<?> handledScreen) {
            if (handledScreen.getScreenHandler() instanceof GenericContainerScreenHandler) {
                // If search is active inside the container, append typed character safely
                if (HandledScreenMixin.isSearchActive) {
                    if (chr >= 32) {
                        HandledScreenMixin.searchQuery += chr;
                    }
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
