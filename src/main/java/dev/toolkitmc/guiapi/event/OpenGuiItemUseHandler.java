package dev.toolkitmc.guiapi.event;

import dev.toolkitmc.guiapi.GuiApiMod;
import dev.toolkitmc.guiapi.component.GuiApiComponents;
import dev.toolkitmc.guiapi.component.OpenGuiComponent;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * Right-click handler for items carrying the {@code guiapi:open_gui} component.
 *
 * Registered once from {@code GuiApiMod#onInitialize()}. Left-click activation
 * is intentionally not implemented — see {@link OpenGuiComponent} for why.
 */
public final class OpenGuiItemUseHandler {

    private OpenGuiItemUseHandler() {}

    public static void register() {
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;

            ItemStack stack = player.getItemInHand(hand);
            OpenGuiComponent data = stack.get(GuiApiComponents.OPEN_GUI);
            if (data == null) return InteractionResult.PASS;

            Identifier guiId = Identifier.tryParse(data.gui());
            if (guiId == null) {
                GuiApiMod.LOGGER.warn("[GuiAPI] open_gui component on item has invalid gui id '{}'.", data.gui());
                return InteractionResult.PASS;
            }

            GuiRegistry.INSTANCE.get(guiId).ifPresentOrElse(
                    def -> BarrelGuiHandler.open(sp, def, data.page()),
                    () -> GuiApiMod.LOGGER.warn("[GuiAPI] open_gui component references unknown gui '{}'.", guiId)
            );

            return InteractionResult.SUCCESS;
        });
    }
}
