package dev.toolkitmc.guiapi.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;

public class GuiScreenHandler extends GenericContainerScreenHandler {

    private final GuiDefinition definition;
    private final int page;

    public GuiScreenHandler(ScreenHandlerType<?> type, int syncId,
                            PlayerInventory playerInv, Inventory inv,
                            int rows, GuiDefinition definition, int page) {
        super(type, syncId, playerInv, inv, rows);
        this.definition = definition;
        this.page = page;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // `getRows()` is from GenericContainerScreenHandler — always matches
        // the actual inventory size, regardless of what GuiDefinition.getRows() returns.
        int guiSlotCount = getRows() * 9;
        if (slotIndex >= 0 && slotIndex < guiSlotCount) {
            if (player instanceof ServerPlayerEntity sp) {
                BarrelGuiHandler.handleClick(sp, definition, page, slotIndex, button, actionType);
            }
            return; // consume; don't call super
        }
        // Block player-inventory clicks (slotIndex >= guiSlotCount) as well — no super call.
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        if (player instanceof ServerPlayerEntity sp) {
            BarrelGuiHandler.onClose(sp); // fires on_close actions
        } else {
            BarrelGuiHandler.onClose(player.getUuid());
        }
    }
}
