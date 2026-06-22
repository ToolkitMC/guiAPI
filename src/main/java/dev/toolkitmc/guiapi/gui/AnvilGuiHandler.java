package dev.toolkitmc.guiapi.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.AnvilScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class AnvilGuiHandler {

    public interface AnvilCallback {
        void onInput(ServerPlayerEntity player, String text);
    }

    private static String getNewItemNameReflected(AnvilScreenHandler handler) {
        try {
            java.lang.reflect.Field field = AnvilScreenHandler.class.getDeclaredField("newItemName");
            field.setAccessible(true);
            String val = (String) field.get(handler);
            return val != null ? val : "";
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = AnvilScreenHandler.class.getDeclaredField("field_30755");
                field.setAccessible(true);
                String val = (String) field.get(handler);
                return val != null ? val : "";
            } catch (Exception e2) {
                ItemStack stack = handler.getSlot(2).getStack();
                if (!stack.isEmpty()) {
                    return stack.getName().getString();
                }
                return "";
            }
        }
    }

    public static void openInput(ServerPlayerEntity player, String title, String defaultText, AnvilCallback callback) {
        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(title);
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                AnvilScreenHandler handler = new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY) {
                    private boolean initializing = false;

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity playerEntity) {
                        if (slotIndex == 2) {
                            String text = getNewItemNameReflected(this);
                            if (playerEntity instanceof ServerPlayerEntity sp) {
                                sp.closeHandledScreen();
                                callback.onInput(sp, text);
                            }
                            return;
                        }
                        super.onSlotClick(slotIndex, button, actionType, playerEntity);
                    }

                    @Override
                    public void updateResult() {
                        // Do not call setStack here — it triggers markDirty → onSlotChange → updateResult loop.
                        // Output slot is managed by super; just update the display name on the output.
                        super.updateResult();
                    }
                };

                // Set the input item once, outside of updateResult, to avoid the recursive loop.
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(defaultText));
                handler.getSlot(0).setStack(paper);

                return handler;
            }
        });
    }
}
