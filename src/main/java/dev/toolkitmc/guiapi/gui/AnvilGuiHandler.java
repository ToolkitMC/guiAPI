package dev.toolkitmc.guiapi.gui;

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
            public net.minecraft.screen.ScreenHandler createMenu(int syncId, PlayerInventory playerInv, PlayerEntity p) {
                AnvilScreenHandler handler = new AnvilScreenHandler(syncId, playerInv, ScreenHandlerContext.EMPTY) {

                    // setNewItemName artık reflection ile okunamaz: AnvilScreenHandler'da
                    // "newItemName" diye bir FIELD yok (Yarn 1.21.4+/1.21.8'de kaldırıldı).
                    // Bunun yerine bu metodu override edip değeri kendimiz tutuyoruz —
                    // client her tuş vuruşunda bu metodu çağırıp text'i senkronize ediyor.
                    private String currentInputText = defaultText;

                    @Override
                    public boolean setNewItemName(String newItemName) {
                        this.currentInputText = newItemName;
                        // super'i çağırmıyoruz: vanilla davranışı maliyet (XP) hesaplayıp
                        // output slotunu yeniden adlandırılmış bir item ile doldurmaya
                        // çalışır. Biz bu GUI'yi salt bir "text input" olarak kullandığımız
                        // için o davranışı hiç istemiyoruz.
                        return true;
                    }

                    @Override
                    public boolean canUse(PlayerEntity player) {
                        return true;
                    }

                    @Override
                    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity playerEntity) {
                        // İstenen davranış: input/lapis/output slotlarından (0, 1, 2)
                        // herhangi birine tıklanması, oyuncu envanteri slotlarına
                        // (3 ve sonrası) DEĞİL, hep aynı "submit" davranışını tetiklesin.
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
                        // Maliyet hesaplama / output item üretme mantığını tamamen
                        // devre dışı bırakıyoruz. super.updateResult() çağırmak vanilla
                        // repair-cost mantığını tetikler ve setStack -> markDirty ->
                        // onContentChanged -> updateResult döngüsüne girebilir.
                        // Bu GUI'de output slotu kullanılmıyor, o yüzden no-op.
                    }
                };

                // Giriş item'ını bir kez burada koy. updateResult() içine koymuyoruz,
                // çünkü setStack çağrısı markDirty tetikler ve updateResult tekrar
                // çağrılır -> sonsuz döngü riski.
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(net.minecraft.component.DataComponentTypes.CUSTOM_NAME, Text.literal(defaultText));
                handler.getSlot(0).setStack(paper);

                return handler;
            }
        });
    }
}
