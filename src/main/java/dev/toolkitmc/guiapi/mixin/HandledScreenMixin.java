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

    @Unique private boolean isSearchActive = false;
    @Unique private String searchQuery = "";

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void renderInject(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler)) return;
        if (!isSearchActive) return;

        long time = System.currentTimeMillis();
        int rainbowColor = java.awt.Color.HSBtoRGB((time % 2000) / 2000f, 0.8f, 0.8f);

        ctx.drawTextWithShadow(textRenderer, "Search: " + searchQuery + "|", 15, 14, rainbowColor);

        for (Slot slot : screen.getScreenHandler().slots) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty()) continue;

            boolean isMatch = searchQuery.isEmpty() ||
                    stack.getName().getString().toLowerCase().contains(searchQuery.toLowerCase()) ||
                    stack.getTooltip(
                            net.minecraft.item.Item.TooltipContext.DEFAULT,
                            client.player,
                            net.minecraft.item.tooltip.TooltipType.BASIC
                    ).stream().anyMatch(t -> t.getString().toLowerCase().contains(searchQuery.toLowerCase()));

            int slotX = this.x + slot.x;
            int slotY = this.y + slot.y;

            if (!searchQuery.isEmpty()) {
                ctx.fill(slotX, slotY, slotX + 16, slotY + 16, isMatch ? 0x4400FF00 : 0xBB000000);
            }
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void charTypedInject(char chr, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler)) return;
        if (!isSearchActive) return;

        searchQuery += chr;
        cir.setReturnValue(true);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void keyPressedInject(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        HandledScreen<?> screen = (HandledScreen<?>)(Object)this;
        if (!(screen.getScreenHandler() instanceof GenericContainerScreenHandler)) return;

        int configuredSearchKey = dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getToggleSearchKey();

        if (keyCode == configuredSearchKey && (modifiers & 2) != 0) {
            isSearchActive = !isSearchActive;
            if (!isSearchActive) searchQuery = "";
            cir.setReturnValue(true);
            return;
        }

        if (!isSearchActive) return;

        if (keyCode == 259) { // BACKSPACE
            if (!searchQuery.isEmpty()) {
                searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            }
        } else if (keyCode == 256) { // ESCAPE
            isSearchActive = false;
            searchQuery = "";
        }

        cir.setReturnValue(true);
    }
}
