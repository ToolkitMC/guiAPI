package dev.toolkitmc.guiapi.gui;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class AnvilGuiHandler {

    public interface AnvilCallback {
        void onInput(ServerPlayer player, String text);
    }

    public static void openInput(ServerPlayer player, String title, String defaultText, AnvilCallback callback) {
        openInput(player, Component.literal(title).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), defaultText, callback);
    }

    public static void openInput(ServerPlayer player, Component title, String defaultText, AnvilCallback callback) {
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AnvilMenu createMenu(int syncId, Inventory playerInv, Player p) {
                AnvilMenu handler = new AnvilMenu(syncId, playerInv, ContainerLevelAccess.NULL) {

                    private String currentInputText = defaultText;

                    @Override
                    public boolean setItemName(String newItemName) {
                        this.currentInputText = newItemName;
                        return true;
                    }

                    @Override
                    public boolean stillValid(Player player) {
                        return true;
                    }

                    @Override
                    public void clicked(int slotId, int button, ContainerInput input, Player playerEntity) {
                        if (slotId == 0 || slotId == 1 || slotId == 2) {
                            if (playerEntity instanceof ServerPlayer sp) {
                                String text = this.currentInputText != null ? this.currentInputText : "";
                                sp.closeContainer();
                                callback.onInput(sp, text);
                            }
                            return;
                        }
                        super.clicked(slotId, button, input, playerEntity);
                    }

                    @Override
                    public void createResult() {
                        // no-op
                    }
                };

                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(DataComponents.CUSTOM_NAME, Component.literal(defaultText));
                handler.getSlot(0).set(paper);

                return handler;
            }
        });
    }
}
