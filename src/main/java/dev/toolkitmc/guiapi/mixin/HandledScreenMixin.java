package dev.toolkitmc.guiapi.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin extends Screen {

    @Shadow protected int x;
    @Shadow protected int y;

    @Unique private static boolean isSearchActive = false;
    @Unique private static String searchQuery = "";

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderInject(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (screen.getScreenHandler() instanceof GenericContainerScreenHandler) {
            if (isSearchActive) {
                long time = System.currentTimeMillis();
                int rainbowColor = java.awt.Color.HSBtoRGB((time % 2000) / 2000f, 0.8f, 0.8f);

                // Draw glowing search bar
                ctx.fill(10, 10, 180, 26, 0x99000000);
                ctx.fill(10, 25, 180, 26, rainbowColor);
                ctx.drawTextWithShadow(textRenderer, "Search: " + searchQuery + "|", 15, 14, 0xFFFFFF);

                // Highlight slot matches
                for (Slot slot : screen.getScreenHandler().slots) {
                    ItemStack stack = slot.getStack();
                    if (stack.isEmpty()) continue;

                    // Match against both item display name and technical item ID safely
                    boolean isMatch = searchQuery.isEmpty() ||
                            stack.getName().getString().toLowerCase().contains(searchQuery.toLowerCase()) ||
                            net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).toString().toLowerCase().contains(searchQuery.toLowerCase());

                    int slotX = this.x + slot.x;
                    int slotY = this.y + slot.y;

                    if (isMatch && !searchQuery.isEmpty()) {
                        ctx.fill(slotX, slotY, slotX + 16, slotY + 16, 0x4400FF00);
                    } else if (!searchQuery.isEmpty()) {
                        ctx.fill(slotX, slotY, slotX + 16, slotY + 16, 0xBB000000);
                    }
                }
            }
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressedInject(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (screen.getScreenHandler() instanceof GenericContainerScreenHandler) {
            
            // Check if the pressed key matches our official client search keybinding (Defaults to L!)
            if (dev.toolkitmc.guiapi.client.GuiApiClient.toggleSearchKey.matchesKey(keyCode, scanCode)) {
                isSearchActive = !isSearchActive;
                if (!isSearchActive) searchQuery = "";
                cir.setReturnValue(true);
                return;
            }

            if (isSearchActive) {
                if (keyCode == 259) { // GLFW_KEY_BACKSPACE = 259
                    if (!searchQuery.isEmpty()) {
                        searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
                    }
                    cir.setReturnValue(true);
                } else if (keyCode == 256) { // GLFW_KEY_ESCAPE = 256
                    isSearchActive = false;
                    searchQuery = "";
                    cir.setReturnValue(true);
                } else {
                    // Consume any other keypresses to block vanilla enventories/hotkeys (like close inventory on E or drop item on Q)
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void charTypedInject(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (screen.getScreenHandler() instanceof GenericContainerScreenHandler) {
            if (isSearchActive) {
                // Only append printable character inputs safely, respecting keyboard layouts
                if (chr >= 32) {
                    searchQuery += chr;
                }
                cir.setReturnValue(true);
            }
        }
    }
}
