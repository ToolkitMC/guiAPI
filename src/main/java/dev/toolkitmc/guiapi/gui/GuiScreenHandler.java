package dev.toolkitmc.guiapi.gui;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;

public class GuiScreenHandler extends ChestMenu {

    private final GuiDefinition definition;
    private final int page;

    public GuiScreenHandler(MenuType<?> type, int syncId,
                            Inventory playerInv, Container container,
                            int rows, GuiDefinition definition, int page) {
        super(type, syncId, playerInv, container, rows);
        this.definition = definition;
        this.page = page;
    }

    public GuiDefinition getDefinition() {
        return definition;
    }

    public int getPage() {
        return page;
    }

    @Override
    public void clicked(int slotId, int button, ContainerInput input, Player player) {
        int guiSlotCount = getRowCount() * 9;
        if (slotId >= 0 && slotId < guiSlotCount) {
            if (player instanceof ServerPlayer sp) {
                BarrelGuiHandler.handleClick(sp, definition, page, slotId, button, input);
            }
            return; // consume; don't call super
        }
        // Block player-inventory clicks as well — no super call.
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer sp) {
            BarrelGuiHandler.onClose(sp);
        } else {
            BarrelGuiHandler.onClose(player.getUUID());
        }
    }
}
