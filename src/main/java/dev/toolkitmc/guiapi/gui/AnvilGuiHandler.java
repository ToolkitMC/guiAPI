package dev.toolkitmc.guiapi.gui;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class AnvilGuiHandler {

    public interface AnvilCallback {
        void onInput(ServerPlayerEntity player, String text);
    }

    public static void openInput(ServerPlayerEntity player, String title, String defaultText, AnvilCallback callback) {
        openInput(player, Text.literal(title).formatted(Formatting.GOLD, Formatting.BOLD), defaultText, callback);
    }

    public static void openInput(ServerPlayerEntity player, Text title, String defaultText, AnvilCallback callback) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return title;
            }

            @Override
            public AnvilScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                AnvilScreenHandler handler = new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY) {

                    // REFLECTION REMOVED: there is no "newItemName" FIELD on AnvilScreenHandler
                    // (removed in Yarn 1.21.4+/1.21.8). getNewItemNameReflected used to always
                    // fall through to the bottom catch block and read the slot 2 item name —
                    // which was never the text the player actually typed. The correct approach
                    // is to override setNewItemName(String) instead.
                    private String currentInputText = defaultText;

                    @Override
                    public boolean setNewItemName(String newItemName) {
                        this.currentInputText = newItemName;
                        // super is not called: the vanilla behavior computes an XP cost and
                        // tries to place a renamed item into the output slot, which is not
                        // wanted in this GUI.
                        return true;
                    }

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
                        // All three slots (0, 1, 2) act as submit; player inventory slots (3+) stay vanilla.
                        if (slotIndex == 0 || slotIndex == 1 || slotIndex == 2) {
                            if (playerEntity instanceof ServerPlayerEntity sp) {
                                String text = this.currentInputText != null ? this.currentInputText : "";
                                sp.closeHandledScreen();
                                callback.onInput(sp, text);
                            }
                            return;
                        }
                        super.onSlotClick(slotIndex, button, actionType, playerEntity);
                    }

                    @Override
                    public void updateResult() {
                        // no-op: super.updateResult() triggers the vanilla repair-cost logic,
                        // risking a setStack -> markDirty -> onContentChanged -> updateResult loop.
                        // Fully disabled since the output slot is unused here.
                    }
                };

                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(DataComponentTypes.CUSTOM_NAME, Text.literal(defaultText));
                handler.getSlot(0).setStack(paper);

                return handler;
            }
        });
    }
}
