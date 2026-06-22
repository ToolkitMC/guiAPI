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

import java.util.Optional;

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
                // Fallback to common Yarn mapping fields for newItemName
                java.lang.reflect.Field field = AnvilScreenHandler.class.getDeclaredField("field_30755");
                field.setAccessible(true);
                String val = (String) field.get(handler);
                return val != null ? val : "";
            } catch (Exception e2) {
                // Ultimate fallback to output slot stack name
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
                return new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY) {
                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, net.minecraft.screen.slot.SlotActionType actionType, PlayerEntity playerEntity) {
                        if (slotIndex == 2) { // Output slot
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
                        ItemStack paper = new ItemStack(Items.PAPER);
                        paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(defaultText));
                        this.input.setStack(0, paper);
                        
                        ItemStack output = new ItemStack(Items.PAPER);
                        output.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(getNewItemNameReflected(this)));
                        this.output.setStack(0, output);
                        
                        this.sendContentUpdates();
                    }
                };
            }
        });
    }
}
