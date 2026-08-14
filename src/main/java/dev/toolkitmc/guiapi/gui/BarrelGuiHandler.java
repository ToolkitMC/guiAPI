package dev.toolkitmc.guiapi.gui;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Optional;

/**
 * Server-side chest GUI handler.
 */
public class BarrelGuiHandler {

    /** Player UUID → currently open GUI state */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, PlayerTickState> OPEN_GUIS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Per-player button cooldown tracking.
     * Player UUID → { "guiId:slot" → tick timestamp of last successful click }.
     * Uses server world time (via ServerPlayer's level) so it stays consistent
     * regardless of TPS drift. Entries are cleared on GUI close alongside vars.
     */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, Map<String, Long>> BUTTON_COOLDOWNS =
            new java.util.concurrent.ConcurrentHashMap<>();

    // Delayed Task Engine queue
    private static final java.util.concurrent.ConcurrentLinkedQueue<DelayedTask> PENDING_TASKS =
            new java.util.concurrent.ConcurrentLinkedQueue<>();

    private static class PlayerTickState {
        final GuiDefinition def;
        final int page;
        int ticksElapsed;
        final double openX;
        final double openY;
        final double openZ;

        PlayerTickState(GuiDefinition def, int page, ServerPlayer player) {
            this.def = def;
            this.page = page;
            this.ticksElapsed = 0;
            this.openX = player.getX();
            this.openY = player.getY();
            this.openZ = player.getZ();
        }
    }

    private record DelayedTask(
            UUID playerUuid,
            List<GuiDefinition.ButtonAction> actions,
            int startIndex,
            int ticksRemaining,
            GuiDefinition def,
            int page
    ) {}

    private enum ScoreModType { SET, ADD, SUB }

    private BarrelGuiHandler() {}

    private static void debug(String msg, Object... args) {
        if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isDebugMode())
            GuiApiMod.LOGGER.info("[GuiAPI|Debug] " + msg, args);
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public static void open(ServerPlayer player, GuiDefinition def) {
        open(player, def, 0);
    }


    private static SimpleContainer populateChestMinecart(ServerPlayer player, GuiDefinition def, int page, net.minecraft.world.entity.vehicle.minecart.MinecartChest cart) {
        SimpleContainer inv = buildInventory(player, def, page, 27);
        for (int i = 0; i < 27; i++) {
            cart.setItem(i, inv.getItem(i));
        }
        return inv;
    }

    public static void open(ServerPlayer player, GuiDefinition def, int page) {
        page = Math.clamp(page, 0, def.getPageCount() - 1);
        int rows = Math.clamp(def.getRows(), 1, 6);
        int finalPage = page;

        // Register state BEFORE building inventory so that any handleClick call
        // triggered during screen open (edge case) already sees the correct state.
        OPEN_GUIS.put(player.getUUID(), new PlayerTickState(def, page, player));
        debug("open: player={} gui={} page={}", player.getScoreboardName(), def.getId(), page);

        SimpleContainer inv;
        int finalRows = rows;
        if (def.getContainerType() == GuiDefinition.ContainerType.CHEST_MINECART) {
            finalRows = 3;
            net.minecraft.server.level.ServerLevel world = (net.minecraft.server.level.ServerLevel) player.level();
            List<net.minecraft.world.entity.vehicle.minecart.MinecartChest> minecarts = world.getEntitiesOfClass(
                net.minecraft.world.entity.vehicle.minecart.MinecartChest.class,
                player.getBoundingBox().inflate(8.0),
                cart -> cart.entityTags().contains("MyGUI")
            );
            if (!minecarts.isEmpty()) {
                inv = populateChestMinecart(player, def, page, minecarts.get(0));
            } else {
                inv = buildInventory(player, def, page, 27);
            }
        } else if (def.getContainerType() == GuiDefinition.ContainerType.ENDER_CHEST) {
            finalRows = 3;
            inv = buildInventory(player, def, page, 27);
        } else if (def.getContainerType() == GuiDefinition.ContainerType.PLAYER) {
            finalRows = 4;
            inv = buildInventory(player, def, page, 36);
        } else {
            inv = buildInventory(player, def, page, rows * 9);
        }

        String resolvedTitle = resolve(def.getTitle(), player, def, page);
        final int openRows = finalRows;

        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal(resolvedTitle);
            }

            @Override
            public net.minecraft.world.inventory.AbstractContainerMenu createMenu(
                    int syncId, Inventory playerInv, Player p) {
                return new GuiScreenHandler(rowsToType(openRows), syncId, playerInv, inv, openRows, def, finalPage);
            }
        });

        // Fire on_open actions — stop if a navigation/close action fires
        executeDelayedActionChain(player, def, page, def.getOnOpen(), 0, false);
    }

    public static void tick(MinecraftServer server) {
        // 1. Tick auto-refresh (tick_rate) and check close_on_move
        for (Map.Entry<UUID, PlayerTickState> entry : OPEN_GUIS.entrySet()) {
            UUID uuid = entry.getKey();
            PlayerTickState state = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(uuid);

            if (player == null) {
                OPEN_GUIS.remove(uuid);
                continue;
            }

            // Check close_on_move
            if (state.def.isCloseOnMove() && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowCloseOnMove()) {
                double dx = player.getX() - state.openX;
                double dy = player.getY() - state.openY;
                double dz = player.getZ() - state.openZ;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > 2.25) { // 1.5 blocks distance squared = 2.25
                    player.closeContainer();
                    player.sendSystemMessage(Component.literal("§cGUI closed due to movement!"), true);
                    continue;
                }
            }

            if (state.def.getTickRate() > 0) {
                state.ticksElapsed++;
                if (state.ticksElapsed >= state.def.getTickRate()) {
                    state.ticksElapsed = 0;
                    refreshCurrentGui(player);
                }
            }
        }

        // 2. Tick pending delayed action tasks
        int taskCount = PENDING_TASKS.size();
        for (int i = 0; i < taskCount; i++) {
            DelayedTask task = PENDING_TASKS.poll();
            if (task == null) break;

            ServerPlayer player = server.getPlayerList().getPlayer(task.playerUuid);
            if (player == null) continue; // Player went offline, drop task

            int remaining = task.ticksRemaining - 1;
            if (remaining <= 0) {
                // Time to execute the next action!
                executeDelayedActionChain(player, task.def, task.page, task.actions, task.startIndex, true);
            } else {
                // Re-queue with decremented tick count
                PENDING_TASKS.add(new DelayedTask(task.playerUuid, task.actions, task.startIndex, remaining, task.def, task.page));
            }
        }
    }

    public static void refreshCurrentGui(ServerPlayer player) {
        if (player.containerMenu instanceof GuiScreenHandler guiHandler) {
            GuiDefinition def = guiHandler.getDefinition();
            int page = guiHandler.getPage();
            int rows = guiHandler.getRowCount();
            int size = rows * 9;
            Container inv = guiHandler.getContainer();

            // Clear inventory first
            for (int slot = 0; slot < size; slot++) {
                inv.setItem(slot, ItemStack.EMPTY);
            }

            // Populate according to page buttons and conditions
            for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
                if (btn.slot() < 0 || btn.slot() >= size) continue;
                if (!shouldRenderButton(player, btn)) continue;
                inv.setItem(btn.slot(), buildStack(player, def, page, btn));
            }

            // Populate non-button widgets (progress bars, static displays)
            populateWidgets(player, def, page, inv, size);

            // Apply background filler for empty slots
            if (def.getFiller().isPresent()) {
                GuiDefinition.FillerConfig fill = def.getFiller().get();
                ItemStack fillStack = buildFillerStack(fill);
                for (int slot = 0; slot < size; slot++) {
                    if (inv.getItem(slot).isEmpty()) {
                        inv.setItem(slot, fillStack.copy());
                    }
                }
            }

            // Send content updates to client
            guiHandler.broadcastChanges();
            debug("refreshCurrentGui: player={} gui={} page={}", player.getScoreboardName(), def.getId(), page);
        }
    }

    public static boolean handleClick(ServerPlayer player, GuiDefinition def,
                                      int page, int slot, int mouseButton, ContainerInput actionType) {
        // mouseButton: 0 = left, 1 = right (Minecraft protocol)
        final boolean isShift = actionType == ContainerInput.QUICK_MOVE;
        final boolean isLeft  = !isShift && mouseButton == 0 && actionType == ContainerInput.PICKUP;
        final boolean isRight = !isShift && mouseButton == 1 && actionType == ContainerInput.PICKUP;

        // Consume every action type to block item manipulation.
        if (!isLeft && !isRight && !isShift) return true;

        for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
            if (btn.slot() != slot) continue;
            if (!evaluateCondition(player, btn)) continue;

            // click_type filter
            boolean matches = switch (btn.clickType()) {
                case LEFT  -> isLeft;
                case RIGHT -> isRight;
                case SHIFT -> isShift;
                case ANY   -> isLeft || isRight || isShift;
            };
            if (!matches) continue;

            if (btn.cooldown() > 0 && isOnCooldown(player, def, btn)) {
                debug("cooldown: player={} gui={} slot={} — click ignored", player.getScoreboardName(), def.getId(), slot);
                return true; // consume the click silently, no spam
            }

            boolean isToggle = btn.toggle().isPresent();
            List<GuiDefinition.ButtonAction> actions = resolveActions(player, btn);

            if (isToggle) {
                GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
                List<GuiDefinition.ButtonAction> toggleActions = resolveActions(player, btn);

                // Flip tag synchronously via Java API
                boolean wasOn = player.entityTags().contains(tgl.tag());
                if (wasOn) player.removeTag(tgl.tag());
                else       player.addTag(tgl.tag());

                if (btn.cooldown() > 0) markCooldown(player, def, btn);

                // Execute all defined actions using Delayed Action Chain Engine
                executeDelayedActionChain(player, def, page, toggleActions, 0, false);
                return true;
            }

            if (btn.cooldown() > 0) markCooldown(player, def, btn);

            // Execute standard click action chain using Delayed Action Chain Engine
            executeDelayedActionChain(player, def, page, actions, 0, false);
            return true;
        }
        return true;
    }

    public static void executeDelayedActionChain(ServerPlayer player, GuiDefinition def, int page, List<GuiDefinition.ButtonAction> actions, int startIndex, boolean ignoreFirstDelay) {
        for (int i = startIndex; i < actions.size(); i++) {
            GuiDefinition.ButtonAction action = actions.get(i);
            boolean checkDelay = (i > startIndex) || !ignoreFirstDelay;
            if (checkDelay && action.delay() > 0 && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowDelayedActions()) {
                // Schedule remaining actions starting from this delayed one!
                PENDING_TASKS.add(new DelayedTask(player.getUUID(), actions, i, action.delay(), def, page));
                break;
            }
            boolean shouldBreak = executeAction(player, def, page, action);
            if (shouldBreak) break;
        }
    }

    public static void onClose(UUID playerUuid) {
        if (OPEN_GUIS.remove(playerUuid) != null) {
            GuiVarStore.INSTANCE.clear(playerUuid);
            GuiInputStore.INSTANCE.clear(playerUuid);
            // Cooldowns intentionally persist across GUI close/reopen — clearing them
            // here would let players bypass cooldowns by closing and reopening the GUI.
        }
    }

    /**
     * Called when the player genuinely closes the GUI (ESC, etc.).
     * Fires on_close actions and clears runtime variables.
     */
    public static void onClose(ServerPlayer player) {
        PlayerTickState state = OPEN_GUIS.remove(player.getUUID());
        if (state == null) return;
        debug("close: player={} gui={}", player.getScoreboardName(), state.def.getId());
        if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isEnableCloseSound()) {
            float closeVolume = 0.5f * (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getSoundVolume() / 100.0f);
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), net.minecraft.sounds.SoundEvents.CHEST_CLOSE, net.minecraft.sounds.SoundSource.BLOCKS, closeVolume, 1.0f);
        }
        for (GuiDefinition.ButtonAction action : state.def.getOnClose()) {
            executeAction(player, state.def, state.page, action);
        }
        GuiVarStore.INSTANCE.clear(player.getUUID());
        GuiInputStore.INSTANCE.clear(player.getUUID());
    }

    /**
     * Called internally before navigating to another GUI or page.
     * Removes open state WITHOUT clearing runtime variables.
     */
    private static void navigateAway(ServerPlayer player) {
        OPEN_GUIS.remove(player.getUUID());
    }

    /** Called from GuiApiMod on ServerPlayConnectionEvents.DISCONNECT to release cooldown state. */
    public static void onPlayerDisconnect(UUID playerUuid) {
        BUTTON_COOLDOWNS.remove(playerUuid);
    }

    // ── Cooldown ─────────────────────────────────────────────────────────────

    private static String cooldownKey(GuiDefinition def, GuiDefinition.Button btn) {
        return def.getId() + ":" + btn.slot();
    }

    private static boolean isOnCooldown(ServerPlayer player, GuiDefinition def, GuiDefinition.Button btn) {
        Map<String, Long> playerCooldowns = BUTTON_COOLDOWNS.get(player.getUUID());
        if (playerCooldowns == null) return false;
        Long lastTick = playerCooldowns.get(cooldownKey(def, btn));
        if (lastTick == null) return false;
        long now = player.level().getServer().getTickCount();
        return (now - lastTick) < btn.cooldown();
    }

    private static void markCooldown(ServerPlayer player, GuiDefinition def, GuiDefinition.Button btn) {
        long now = player.level().getServer().getTickCount();
        BUTTON_COOLDOWNS
                .computeIfAbsent(player.getUUID(), k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(cooldownKey(def, btn), now);
    }

    // ── Inventory builder ────────────────────────────────────────────────────

    private static SimpleContainer buildInventory(ServerPlayer player,
                                                  GuiDefinition def, int page, int size) {
        SimpleContainer inv = new SimpleContainer(size) {
            @Override public boolean stillValid(Player p) { return true; }
        };

        for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
            if (btn.slot() < 0 || btn.slot() >= size) continue;
            if (!shouldRenderButton(player, btn)) continue;
            inv.setItem(btn.slot(), buildStack(player, def, page, btn));
        }

        // Populate non-button widgets (progress bars, static displays)
        populateWidgets(player, def, page, inv, size);

        // Apply background filler for empty slots
        if (def.getFiller().isPresent()) {
            GuiDefinition.FillerConfig fill = def.getFiller().get();
            ItemStack fillStack = buildFillerStack(fill);
            for (int slot = 0; slot < size; slot++) {
                if (inv.getItem(slot).isEmpty()) {
                    inv.setItem(slot, fillStack.copy());
                }
            }
        }

        return inv;
    }

    // ── Widget rendering (progress bars, static displays) ──────────────────────

    private static void populateWidgets(ServerPlayer player, GuiDefinition def, int page, Container inv, int size) {
        for (GuiDefinition.ProgressBarWidget bar : def.getProgressBarsForPage(page)) {
            renderProgressBar(player, def, page, bar, inv, size);
        }
        for (GuiDefinition.StaticDisplayWidget disp : def.getDisplaysForPage(page)) {
            if (disp.slot() < 0 || disp.slot() >= size) continue;
            if (disp.condition().isPresent() && !evaluateStaticCondition(player, disp.condition().get())) continue;
            inv.setItem(disp.slot(), buildStaticDisplayStack(player, def, page, disp));
        }
    }

    private static boolean evaluateStaticCondition(ServerPlayer player, GuiDefinition.ButtonCondition cond) {
        // Reuses the same condition logic as buttons by wrapping into a throwaway
        // Button-shaped evaluation — avoids duplicating the switch in evaluateCondition.
        GuiDefinition.Button fake = new GuiDefinition.Button(
                0, 0, "", "", List.of(), false, GuiDefinition.ClickType.ANY,
                Optional.of(cond), List.of(), Optional.empty(), Optional.empty(), Optional.empty(),
                "1", false, false, Optional.empty(), 0);
        return evaluateCondition(player, fake);
    }

    private static int readWidgetValue(ServerPlayer player, String valueSource) {
        String[] parts = valueSource.split(":", 2);
        if (parts.length != 2) return 0;
        String kind = parts[0].trim().toLowerCase();
        String key = parts[1].trim();
        return switch (kind) {
            case "score" -> getScore(player, key);
            case "var"   -> GuiVarStore.INSTANCE.getInt(player.getUUID(), key);
            default -> 0;
        };
    }

    private static void renderProgressBar(ServerPlayer player, GuiDefinition def, int page,
                                          GuiDefinition.ProgressBarWidget bar, Container inv, int size) {
        int value = Math.clamp(readWidgetValue(player, bar.valueSource()), 0, bar.maxValue());
        int filledSlots = bar.maxValue() <= 0 ? 0
                : (int) Math.round((value / (double) bar.maxValue()) * bar.length());
        filledSlots = Math.clamp(filledSlots, 0, bar.length());

        Identifier filledId = Identifier.tryParse(bar.filledItem());
        Identifier emptyId  = Identifier.tryParse(bar.emptyItem());
        Identifier defaultFilledId = Identifier.tryParse("minecraft:lime_stained_glass_pane");
        Identifier defaultEmptyId  = Identifier.tryParse("minecraft:gray_stained_glass_pane");
        Item filledItem = (filledId != null && BuiltInRegistries.ITEM.containsKey(filledId))
                ? BuiltInRegistries.ITEM.getValue(filledId) : BuiltInRegistries.ITEM.getValue(defaultFilledId);
        Item emptyItem = (emptyId != null && BuiltInRegistries.ITEM.containsKey(emptyId))
                ? BuiltInRegistries.ITEM.getValue(emptyId) : BuiltInRegistries.ITEM.getValue(defaultEmptyId);

        for (int i = 0; i < bar.length(); i++) {
            int slot = bar.startSlot() + i;
            if (slot < 0 || slot >= size) continue;
            boolean filled = i < filledSlots;
            ItemStack stack = new ItemStack(filled ? filledItem : emptyItem);
            String resolvedName = resolve(bar.name(), player, def, page);
            if (!resolvedName.isEmpty()) {
                stack.set(DataComponents.CUSTOM_NAME,
                        Component.literal(resolvedName).withStyle(s -> s.withItalic(false)));
            }
            if (!bar.lore().isEmpty()) {
                List<Component> loreTexts = new java.util.ArrayList<>();
                for (String l : bar.lore()) {
                    loreTexts.add(Component.literal(resolve(l, player, def, page)).withStyle(s -> s.withItalic(false)));
                }
                stack.set(DataComponents.LORE, new ItemLore(loreTexts));
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
            net.minecraft.world.item.component.TooltipDisplay tooltipDisplay =
                    new net.minecraft.world.item.component.TooltipDisplay(false, new java.util.LinkedHashSet<>());
            if (resolvedName.isEmpty() && bar.lore().isEmpty()) {
                stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
            }
            inv.setItem(slot, stack);
        }
    }

    private static ItemStack buildStaticDisplayStack(ServerPlayer player, GuiDefinition def, int page,
                                                      GuiDefinition.StaticDisplayWidget disp) {
        Identifier id = Identifier.tryParse(resolve(disp.item(), player, def, page));
        Item item = (id != null && BuiltInRegistries.ITEM.containsKey(id))
                ? BuiltInRegistries.ITEM.getValue(id) : Items.PAPER;

        int amount = 1;
        try {
            amount = Math.clamp(Integer.parseInt(resolve(disp.amount(), player, def, page)), 1, 99);
        } catch (NumberFormatException ignored) {}

        ItemStack stack = new ItemStack(item, amount);
        String resolvedName = resolve(disp.name(), player, def, page);
        if (!resolvedName.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(resolvedName).withStyle(s -> s.withItalic(false)));
        }
        if (!disp.lore().isEmpty()) {
            List<Component> loreTexts = new java.util.ArrayList<>();
            for (String l : disp.lore()) {
                loreTexts.add(Component.literal(resolve(l, player, def, page)).withStyle(s -> s.withItalic(false)));
            }
            stack.set(DataComponents.LORE, new ItemLore(loreTexts));
        }
        if (disp.glint() && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isEnableButtonGlint()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        return stack;
    }

    private static ItemStack buildFillerStack(GuiDefinition.FillerConfig fill) {
        Identifier id = Identifier.tryParse(fill.item());
        Item item = Items.STONE;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            item = BuiltInRegistries.ITEM.getValue(id);
        }
        ItemStack stack = new ItemStack(item);
        if (!fill.name().isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(fill.name()).withStyle(s -> s.withItalic(false)));
        }
        if (fill.glint() && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isEnableButtonGlint()) {
            stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        if (fill.hideTooltip()) {
            // Pristine fix: Match Java 21 SequencedSet for empty list
            net.minecraft.world.item.component.TooltipDisplay tooltipDisplay =
                    new net.minecraft.world.item.component.TooltipDisplay(false, new java.util.LinkedHashSet<>());
            stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
        }
        return stack;
    }

    private static ItemStack buildStack(ServerPlayer player,
                                        GuiDefinition def, int page,
                                        GuiDefinition.Button btn) {
        final String  itemId;
        final String  name;
        final List<String> lore;
        final boolean glint;
        final Optional<GuiDefinition.CustomModelDataConfig> customModelData;
        final Optional<String> itemModel;
        final String amountStr;
        final boolean hideTooltip;
        final boolean hideAdditionalTooltip;

        if (btn.toggle().isPresent()) {
            GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
            boolean on = player.entityTags().contains(tgl.tag());
            itemId = on ? tgl.itemOn()  : tgl.itemOff();
            name   = on ? tgl.nameOn()  : tgl.nameOff();
            lore   = on ? tgl.loreOn()  : tgl.loreOff();
            glint  = on ? tgl.glintOn() : tgl.glintOff();
            customModelData = on ? tgl.customModelDataOn() : tgl.customModelDataOff();
            itemModel = on ? tgl.itemModelOn() : tgl.itemModelOff();
            amountStr = on ? tgl.amountOn() : tgl.amountOff();
            hideTooltip = on ? tgl.hideTooltipOn() : tgl.hideTooltipOff();
            hideAdditionalTooltip = on ? tgl.hideAdditionalTooltipOn() : tgl.hideAdditionalTooltipOff();
        } else if (btn.elseDisplay().isPresent() && btn.condition().isPresent()
                && !evaluateCondition(player, btn)) {
            // Condition failed but an else_item was defined — show the alternate
            // appearance instead of the button's normal item (button stays visible).
            GuiDefinition.ConditionalDisplay alt = btn.elseDisplay().get();
            itemId = alt.item();
            name   = alt.name();
            lore   = alt.lore();
            glint  = alt.glint();
            customModelData = alt.customModelData();
            itemModel = alt.itemModel();
            amountStr = alt.amount();
            hideTooltip = alt.hideTooltip();
            hideAdditionalTooltip = alt.hideAdditionalTooltip();
        } else {
            itemId = btn.item();
            name   = btn.name();
            lore   = btn.lore();
            glint  = btn.glint();
            customModelData = btn.customModelData();
            itemModel = btn.itemModel();
            amountStr = btn.amount();
            hideTooltip = btn.hideTooltip();
            hideAdditionalTooltip = btn.hideAdditionalTooltip();
        }

        // Resolve placeholders in the item id itself (e.g. "{score:tier}" → "minecraft:diamond")
        // so a button's item can change dynamically based on player state.
        String resolvedItemId = resolve(itemId, player, def, page);

        Identifier id = Identifier.tryParse(resolvedItemId);
        Item item;
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            item = BuiltInRegistries.ITEM.getValue(id);
        } else {
            if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogUnknownItems())
                GuiApiMod.LOGGER.warn("[GuiAPI] Unknown item '{}' (resolved from '{}') in slot {}, falling back to stone.", resolvedItemId, itemId, btn.slot());
            item = Items.STONE;
        }

        // Parse and resolve dynamic stack amount
        String resolvedAmount = resolve(amountStr, player, def, page);
        int amount = 1;
        try {
            amount = Math.clamp(Integer.parseInt(resolvedAmount), 1, 99);
        } catch (NumberFormatException ignored) {}

        ItemStack stack = new ItemStack(item, amount);

        String resolvedName = resolve(name, player, def, page);
        if (!resolvedName.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME,
                    Component.literal(resolvedName).withStyle(s -> s.withItalic(false)));
        }

        List<Component> loreTexts = new java.util.ArrayList<>();
        if (!lore.isEmpty()) {
            for (String l : lore) {
                loreTexts.add(Component.literal(resolve(l, player, def, page)).withStyle(s -> s.withItalic(false)));
            }
        }
        if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isShowItemIdsDeveloper()) {
            loreTexts.add(Component.literal("§8ID: " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)).withStyle(s -> s.withItalic(false)));
        }
        if (!loreTexts.isEmpty()) {
            stack.set(DataComponents.LORE, new ItemLore(loreTexts));
        }

        if (glint && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isEnableButtonGlint()) stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Mark as GUI item to block extraction
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));

        // --- Custom Model Data ---
        if (customModelData.isPresent()) {
            GuiDefinition.CustomModelDataConfig cmd = customModelData.get();
            net.minecraft.world.item.component.CustomModelData component =
                    new net.minecraft.world.item.component.CustomModelData(
                            cmd.floats(), cmd.flags(), cmd.strings(), cmd.colors()
                    );
            stack.set(DataComponents.CUSTOM_MODEL_DATA, component);
        }

        // --- Item Model ---
        if (itemModel.isPresent()) {
            Identifier modelId = Identifier.tryParse(itemModel.get());
            if (modelId != null) {
                stack.set(DataComponents.ITEM_MODEL, modelId);
            }
        }

        // --- Tooltip Control (1.21.4+ TOOLTIP_DISPLAY Component) ---
        if (hideTooltip) {
            // Pristine fix: Match Java 21 SequencedSet for empty list
            net.minecraft.world.item.component.TooltipDisplay tooltipDisplay =
                    new net.minecraft.world.item.component.TooltipDisplay(false, new java.util.LinkedHashSet<>());
            stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
        } else if (hideAdditionalTooltip) {
            // Pristine fix: Match Java 21 SequencedSet constructor parameter
            net.minecraft.world.item.component.TooltipDisplay tooltipDisplay =
                    new net.minecraft.world.item.component.TooltipDisplay(true, new java.util.LinkedHashSet<>(java.util.List.of(
                            DataComponents.ATTRIBUTE_MODIFIERS,
                            DataComponents.ENCHANTMENTS,
                            DataComponents.STORED_ENCHANTMENTS,
                            DataComponents.DYED_COLOR,
                            DataComponents.POTION_CONTENTS,
                            DataComponents.UNBREAKABLE
                    )));
            stack.set(DataComponents.TOOLTIP_DISPLAY, tooltipDisplay);
        }

        return stack;
    }

    // ── Placeholder resolution ────────────────────────────────────────────────

    static String resolve(String text, ServerPlayer player,
                          GuiDefinition def, int page) {
        if (text == null || text.isEmpty() || !text.contains("{")) return text;

        text = text.replace("{player}", player.getDisplayName().getString());
        text = text.replace("{gui}",    def.getId().toString());
        text = text.replace("{page}",   String.valueOf(page));
        text = text.replace("{page1}",  String.valueOf(page + 1));
        text = text.replace("{pages}",  String.valueOf(def.getPageCount()));
        text = text.replace("{xp}",     String.valueOf(player.experienceLevel));
        text = text.replace("{input}",  GuiInputStore.INSTANCE.get(player.getUUID()));

        // {score:objective}
        int idx;
        while ((idx = text.indexOf("{score:")) >= 0) {
            int end = text.indexOf('}', idx);
            if (end < 0) break;
            String obj = text.substring(idx + 7, end);
            int score = getScore(player, obj);
            text = text.substring(0, idx) + score + text.substring(end + 1);
        }

        // {var:key}
        while ((idx = text.indexOf("{var:")) >= 0) {
            int end = text.indexOf('}', idx);
            if (end < 0) break;
            String key = text.substring(idx + 5, end);
            String val = GuiVarStore.INSTANCE.getOrDefault(player.getUUID(), key, "");
            text = text.substring(0, idx) + val + text.substring(end + 1);
        }

        debug("resolve: \"{}\" → \"{}\"", text.length() > 60 ? text.substring(0, 60) + "..." : text, text);
        return text;
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    /**
     * Whether a button should be rendered in the inventory at all.
     * Normally identical to {@link #evaluateCondition}, but a button with an
     * {@code else_item} defined stays visible (showing the alternate appearance)
     * even when its condition is false, instead of being hidden.
     * Clickability is NOT affected by this — {@link #handleClick} still gates
     * on {@link #evaluateCondition} directly, so an else-item button is visible
     * but inert while its condition is false.
     */
    static boolean shouldRenderButton(ServerPlayer player, GuiDefinition.Button btn) {
        if (btn.elseDisplay().isPresent() && btn.condition().isPresent()) return true;
        return evaluateCondition(player, btn);
    }

    static boolean evaluateCondition(ServerPlayer player, GuiDefinition.Button btn) {
        if (btn.condition().isEmpty()) return true;

        GuiDefinition.ButtonCondition cond = btn.condition().get();
        return switch (cond.type()) {
            case HAS_TAG  -> player.entityTags().contains(cond.value());
            case NOT_TAG  -> !player.entityTags().contains(cond.value());
            case SCORE_GT -> getScore(player, cond.value().split(":", 2), 0) >
                             parseCondInt(cond.value().split(":", 2), 1);
            case SCORE_LT -> getScore(player, cond.value().split(":", 2), 0) <
                             parseCondInt(cond.value().split(":", 2), 1);
            case SCORE_EQ -> getScore(player, cond.value().split(":", 2), 0) ==
                             parseCondInt(cond.value().split(":", 2), 1);
            // var conditions — value format: "varKey:compareValue"
            case VAR_EQ   -> {
                String[] p = cond.value().split(":", 2);
                yield p.length == 2 && GuiVarStore.INSTANCE
                        .getOrDefault(player.getUUID(), p[0], "").equals(p[1]);
            }
            case VAR_GT   -> {
                String[] p = cond.value().split(":", 2);
                yield p.length == 2 && GuiVarStore.INSTANCE
                        .getInt(player.getUUID(), p[0]) > parseIntSafe(p[1]);
            }
            case VAR_LT   -> {
                String[] p = cond.value().split(":", 2);
                yield p.length == 2 && GuiVarStore.INSTANCE
                        .getInt(player.getUUID(), p[0]) < parseIntSafe(p[1]);
            }
            case VAR_SET  -> GuiVarStore.INSTANCE.get(player.getUUID(), cond.value()) != null;
            case HAS_ITEM -> {
                String[] parts = cond.value().split(":", 2);
                String itemId = parts[0];
                int amount = parts.length > 1 ? parseIntSafe(parts[1]) : 1;
                yield hasItemCount(player, itemId, amount);
            }
            case NOT_ITEM -> {
                String[] parts = cond.value().split(":", 2);
                String itemId = parts[0];
                int amount = parts.length > 1 ? parseIntSafe(parts[1]) : 1;
                yield !hasItemCount(player, itemId, amount);
            }
            case LEVEL_GT -> player.experienceLevel > parseIntSafe(cond.value());
            case LEVEL_LT -> player.experienceLevel < parseIntSafe(cond.value());
            case HEALTH_GT -> player.getHealth() > parseFloatSafe(cond.value());
            case HEALTH_LT -> player.getHealth() < parseFloatSafe(cond.value());
            case FOOD_GT -> player.getFoodData().getFoodLevel() > parseIntSafe(cond.value());
            case FOOD_LT -> player.getFoodData().getFoodLevel() < parseIntSafe(cond.value());
            // PERMISSION condition — value is the required permission level (0-4).
            // Uses the same Permission.HasCommandLevel API already used by GuiCommand
            // for the /guiapi command's own permission gate (confirmed working there).
            case PERMISSION -> {
                int requiredLevel = Math.clamp(parseIntSafe(cond.value()), 0, 4);
                yield player.createCommandSourceStack().permissions().hasPermission(
                        new net.minecraft.server.permissions.Permission.HasCommandLevel(
                                net.minecraft.server.permissions.PermissionLevel.byId(requiredLevel)));
            }
            // GAMEMODE — value is survival | creative | adventure | spectator
            case GAMEMODE -> {
                net.minecraft.world.level.GameType current = player.gameMode.getGameModeForPlayer();
                yield current.getName().equalsIgnoreCase(cond.value().trim());
            }
            // IN_DIMENSION — value is a dimension id, e.g. minecraft:the_nether
            case IN_DIMENSION -> {
                Identifier dimId = Identifier.tryParse(cond.value().trim());
                yield dimId != null && player.level().dimension().toString().equals(dimId.toString());
            }
        };
    }

    private static boolean hasItemCount(ServerPlayer player, String itemId, int requiredAmount) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return false;
        Item targetItem = BuiltInRegistries.ITEM.getValue(id);

        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                count += stack.getCount();
            }
        }
        return count >= requiredAmount;
    }

    private static void takeItemCount(ServerPlayer player, String itemId, int amountToTake) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return;
        Item targetItem = BuiltInRegistries.ITEM.getValue(id);

        int remaining = amountToTake;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.is(targetItem)) {
                int count = stack.getCount();
                if (count <= remaining) {
                    player.getInventory().setItem(i, ItemStack.EMPTY);
                    remaining -= count;
                } else {
                    stack.shrink(remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
        }
        // Sync player inventory with client
        player.containerMenu.broadcastChanges();
    }

    /**
     * Gives the player {@code amount} of {@code itemId}, splitting across the item's
     * max stack size. Overflow that doesn't fit in the inventory is dropped at the
     * player's feet (matches vanilla /give behavior) rather than silently discarded.
     */
    private static void giveItemCount(ServerPlayer player, String itemId, int amount) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogUnknownItems())
                GuiApiMod.LOGGER.warn("[GuiAPI] Unknown item '{}' in give_item action.", itemId);
            return;
        }
        Item targetItem = BuiltInRegistries.ITEM.getValue(id);
        int remaining = amount;
        while (remaining > 0) {
            ItemStack stack = new ItemStack(targetItem);
            int give = Math.min(remaining, stack.getMaxStackSize());
            stack.setCount(give);
            remaining -= give;
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.containerMenu.broadcastChanges();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static float parseFloatSafe(String s) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return 0.0f; }
    }

    // ── Toggle action resolution ─────────────────────────────────────────────

    private static List<GuiDefinition.ButtonAction> resolveActions(
            ServerPlayer player, GuiDefinition.Button btn) {
        if (btn.toggle().isPresent()) {
            GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
            boolean on = player.entityTags().contains(tgl.tag());
            return on ? tgl.actionsOn() : tgl.actionsOff();
        }
        return btn.actions();
    }

    // ── Action execution ─────────────────────────────────────────────────────

    static boolean executeAction(ServerPlayer player, GuiDefinition def,
                                 int currentPage, GuiDefinition.ButtonAction action) {
        MinecraftServer server = player.level().getServer();
        debug("action: player={} type={} value=\"{}\"",
                player.getScoreboardName(), action.type(), action.value());
        switch (action.type()) {
            case RUN_COMMAND -> {
                String cmd = action.value().startsWith("/")
                        ? action.value().substring(1) : action.value();
                cmd = resolve(cmd, player, def, currentPage);
                // Auditing executed command logs if enabled globally in config
                if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogCommands()) {
                    GuiApiMod.LOGGER.info("[GuiAPI|CommandLog] Player {} executed command: {}", player.getScoreboardName(), cmd);
                }
                if (action.runWith() == GuiDefinition.RunWith.CONSOLE) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowConsoleRunWith()) {
                        if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isMuteClickErrors()) {
                            GuiApiMod.LOGGER.warn("[GuiAPI] run_with:console is disabled in config. Skipping: {}", cmd);
                        }
                        break;
                    }
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
                } else {
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), cmd);
                }
            }
            case NONE -> {
                return true;
            }
            case ANVIL_INPUT -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String varKey = action.var().isEmpty() ? "input" : action.var();
                String anvilTitle;
                String defaultText;
                if (resolved.contains("|")) {
                    String[] p = resolved.split("\\|", 2);
                    anvilTitle = p[0];
                    defaultText = p[1];
                } else {
                    anvilTitle = resolved;
                    defaultText = "Type here...";
                }
                final Identifier previousGuiId = def.getId();
                final int previousPage = currentPage;

                AnvilGuiHandler.openInput(player, anvilTitle, defaultText, (sp, text) -> {
                    GuiVarStore.INSTANCE.set(sp.getUUID(), varKey, text);
                    GuiInputStore.INSTANCE.set(sp.getUUID(), text);
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE.get(previousGuiId)
                            .ifPresent(target -> open(sp, target, previousPage));
                });
            }
            case RUN_FUNCTION -> {
                String macroName = resolve(action.value(), player, def, currentPage);
                java.util.List<GuiDefinition.ButtonAction> macroActions = def.getMacros().get(macroName);
                if (macroActions != null && !macroActions.isEmpty()) {
                    executeDelayedActionChain(player, def, currentPage, macroActions, 0, false);
                }
            }
            case RUN_RANDOM_FUNCTION -> {
                // value: comma-separated macro names, each optionally weighted "name*weight"
                // e.g. "common*70,rare*25,legendary*5" — plain "name" defaults to weight 1.
                String resolvedList = resolve(action.value(), player, def, currentPage);
                String[] entries = resolvedList.split(",");
                java.util.List<String> pool = new java.util.ArrayList<>();
                for (String entry : entries) {
                    String trimmed = entry.trim();
                    if (trimmed.isEmpty()) continue;
                    String name = trimmed;
                    int weight = 1;
                    int star = trimmed.indexOf('*');
                    if (star > 0) {
                        name = trimmed.substring(0, star).trim();
                        weight = Math.max(1, parseIntSafe(trimmed.substring(star + 1).trim()));
                    }
                    if (!def.getMacros().containsKey(name)) continue;
                    for (int w = 0; w < weight; w++) pool.add(name);
                }
                if (!pool.isEmpty()) {
                    String chosen = pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
                    java.util.List<GuiDefinition.ButtonAction> macroActions = def.getMacros().get(chosen);
                    debug("random_function: player={} chosen={} pool_size={}", player.getScoreboardName(), chosen, pool.size());
                    if (macroActions != null && !macroActions.isEmpty()) {
                        executeDelayedActionChain(player, def, currentPage, macroActions, 0, false);
                    }
                } else {
                    GuiApiMod.LOGGER.warn("[GuiAPI] run_random_function had no valid macros to choose from: {}", resolvedList);
                }
            }
            case SET_GAMEMODE -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowGamemodeChange()) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isMuteClickErrors()) {
                        GuiApiMod.LOGGER.warn("[GuiAPI] set_gamemode is disabled in config. Skipping.");
                    }
                    break;
                }
                String resolvedGm = resolve(action.value(), player, def, currentPage).trim().toLowerCase();
                net.minecraft.world.level.GameType target = switch (resolvedGm) {
                    case "survival"  -> net.minecraft.world.level.GameType.SURVIVAL;
                    case "creative"  -> net.minecraft.world.level.GameType.CREATIVE;
                    case "adventure" -> net.minecraft.world.level.GameType.ADVENTURE;
                    case "spectator" -> net.minecraft.world.level.GameType.SPECTATOR;
                    default -> null;
                };
                if (target != null) {
                    player.setGameMode(target);
                } else {
                    GuiApiMod.LOGGER.warn("[GuiAPI] Unknown gamemode '{}' in set_gamemode action.", resolvedGm);
                }
            }
            case CLOSE -> {
                player.closeContainer();
                return true;
            }
            case OPEN_GUI -> {
                navigateAway(player);
                player.closeContainer();
                Identifier targetId = Identifier.tryParse(action.value());
                if (targetId != null) {
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE
                            .get(targetId)
                            .ifPresentOrElse(
                                    target -> open(player, target),
                                    () -> player.sendSystemMessage(
                                            Component.literal("[GuiAPI] GUI not found: " + targetId), false));
                }
                return true;
            }
            case MESSAGE -> {
                String msgVal = resolve(action.value(), player, def, currentPage);
                String mode = dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getCommandExecuteMode();
                if ("CHAT".equalsIgnoreCase(mode)) {
                    player.sendSystemMessage(Component.literal(msgVal), false);
                } else if ("SYSTEM".equalsIgnoreCase(mode)) {
                    player.sendSystemMessage(Component.literal(msgVal), true);
                }
            }
            case NEXT_PAGE -> {
                int next = currentPage + 1;
                if (next < def.getPageCount()) {
                    navigateAway(player);
                    player.closeContainer();
                    open(player, def, next);
                }
                return true;
            }
            case PREV_PAGE -> {
                int prev = currentPage - 1;
                if (prev >= 0) {
                    navigateAway(player);
                    player.closeContainer();
                    open(player, def, prev);
                }
                return true;
            }
            case SOUND -> {
                String resolvedSound = resolve(action.value(), player, def, currentPage);
                String[] parts = resolvedSound.split(":");
                String soundId;
                float volume = 1.0f;
                float pitch  = 1.0f;
                if (parts.length >= 4) {
                    soundId = parts[0] + ":" + parts[1];
                    try { volume = Float.parseFloat(parts[2]); } catch (NumberFormatException ignored) {}
                    try { pitch  = Float.parseFloat(parts[3]); } catch (NumberFormatException ignored) {}
                } else if (parts.length == 3) {
                    soundId = parts[0] + ":" + parts[1];
                    try { volume = Float.parseFloat(parts[2]); } catch (NumberFormatException ignored) {}
                } else {
                    soundId = resolvedSound;
                }
                Identifier soundIdent = Identifier.tryParse(soundId);
                if (soundIdent != null) {
                    net.minecraft.sounds.SoundEvent soundEvent =
                            BuiltInRegistries.SOUND_EVENT.getValue(soundIdent);
                    if (soundEvent != null) {
                        float finalVolume = volume * (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getSoundVolume() / 100.0f);
                        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), soundEvent,
                                net.minecraft.sounds.SoundSource.PLAYERS, finalVolume, pitch);
                    } else {
                        if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogUnknownSounds())
                            GuiApiMod.LOGGER.warn("[GuiAPI] Unknown sound '{}' in sound action.", soundId);
                    }
                }
            }
            case GOTO_PAGE -> {
                try {
                    int target = Integer.parseInt(action.value());
                    if (target >= 0 && target < def.getPageCount()) {
                        navigateAway(player);
                        player.closeContainer();
                        open(player, def, target);
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
            case SET_VAR -> {
                if (!action.var().isEmpty()) {
                    String resolved = resolve(action.value(), player, def, currentPage);
                    GuiVarStore.INSTANCE.set(player.getUUID(), action.var(), resolved);
                }
            }
            case ADD_VAR -> {
                if (!action.var().isEmpty()) {
                    try {
                        int delta = Integer.parseInt(resolve(action.value(), player, def, currentPage));
                        GuiVarStore.INSTANCE.add(player.getUUID(), action.var(), delta);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case SUB_VAR -> {
                if (!action.var().isEmpty()) {
                    try {
                        int delta = Integer.parseInt(resolve(action.value(), player, def, currentPage));
                        GuiVarStore.INSTANCE.add(player.getUUID(), action.var(), -delta);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case RESET_VAR -> {
                if (!action.var().isEmpty()) {
                    GuiVarStore.INSTANCE.remove(player.getUUID(), action.var());
                }
            }
            case CLEAR_VARS -> GuiVarStore.INSTANCE.clear(player.getUUID());
            case REFRESH -> refreshCurrentGui(player);
            case TAKE_ITEM -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":", 2);
                String itemId = parts[0];
                int amount = parts.length > 1 ? parseIntSafe(parts[1]) : 1;
                takeItemCount(player, itemId, amount);
            }
            case GIVE_ITEM -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":", 2);
                String itemId = parts[0];
                int amount = parts.length > 1 ? Math.max(1, parseIntSafe(parts[1])) : 1;
                giveItemCount(player, itemId, amount);
            }
            case ADD_XP -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                boolean isLevels = resolved.startsWith("L") || resolved.startsWith("l");
                String numeric = isLevels ? resolved.substring(1) : resolved;
                try {
                    int amount = Integer.parseInt(numeric.trim());
                    if (isLevels) {
                        player.giveExperienceLevels(amount);
                    } else {
                        player.giveExperiencePoints(amount);
                    }
                } catch (NumberFormatException ignored) {}
            }
            case SET_SCORE -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":", 2);
                if (parts.length == 2) {
                    try {
                        String objName = parts[0];
                        int val = Integer.parseInt(parts[1]);
                        modifyScore(player, objName, val, ScoreModType.SET);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case ADD_SCORE -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":", 2);
                if (parts.length == 2) {
                    try {
                        String objName = parts[0];
                        int val = Integer.parseInt(parts[1]);
                        modifyScore(player, objName, val, ScoreModType.ADD);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case SUB_SCORE -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":", 2);
                if (parts.length == 2) {
                    try {
                        String objName = parts[0];
                        int val = Integer.parseInt(parts[1]);
                        modifyScore(player, objName, val, ScoreModType.SUB);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case ACTION_BAR -> {
                String resolved = resolve(action.value(), player, def, currentPage);
                player.sendSystemMessage(Component.literal(resolved), true);
            }
            case ADD_EFFECT -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isMuteClickErrors()) {
                        GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    }
                    break;
                }
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":");
                if (parts.length >= 2) {
                    try {
                        String effectId;
                        int durationIdx = 1;

                        boolean hasNamespace = false;
                        if (parts.length >= 3) {
                            try {
                                Integer.parseInt(parts[1]);
                            } catch (NumberFormatException e) {
                                hasNamespace = true;
                            }
                        }

                        if (hasNamespace) {
                            effectId = parts[0] + ":" + parts[1];
                            durationIdx = 2;
                        } else {
                            effectId = parts[0];
                            durationIdx = 1;
                        }

                        int durationSecs = Integer.parseInt(parts[durationIdx]);
                        int amp = parts.length > (durationIdx + 1) ? Integer.parseInt(parts[durationIdx + 1]) : 0;
                        boolean particles = parts.length <= (durationIdx + 2) || Boolean.parseBoolean(parts[durationIdx + 2]);

                        Identifier effIdent = Identifier.tryParse(effectId);
                        if (effIdent != null && BuiltInRegistries.MOB_EFFECT.containsKey(effIdent)) {
                            var holderOpt = BuiltInRegistries.MOB_EFFECT.get(effIdent);
                            if (holderOpt.isPresent()) {
                                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                                        holderOpt.get(), durationSecs * 20, amp, false, particles, particles
                                ));
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            case REMOVE_EFFECT -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isMuteClickErrors()) {
                        GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    }
                    break;
                }
                String resolved = resolve(action.value(), player, def, currentPage);
                Identifier effIdent = Identifier.tryParse(resolved);
                if (effIdent != null && BuiltInRegistries.MOB_EFFECT.containsKey(effIdent)) {
                    var holderOpt2 = BuiltInRegistries.MOB_EFFECT.get(effIdent);
                    if (holderOpt2.isPresent()) {
                        player.removeEffect(holderOpt2.get());
                    }
                }
            }
            case CLEAR_EFFECTS -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isMuteClickErrors()) {
                        GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    }
                    break;
                }
                player.removeAllEffects();
            }
        }
        return false;
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

    private static void modifyScore(ServerPlayer player, String objectiveName, int val, ScoreModType modType) {
        try {
            Scoreboard sb = player.level().getServer().getScoreboard();
            Objective obj = sb.getObjective(objectiveName);
            if (obj == null) return;
            net.minecraft.world.scores.ScoreAccess score = sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), obj);
            if (score != null) {
                int current = score.get();
                int target = switch (modType) {
                    case SET -> val;
                    case ADD -> current + val;
                    case SUB -> current - val;
                };
                score.set(target);
            }
        } catch (Exception e) {
            GuiApiMod.LOGGER.error("[GuiAPI] Failed to modify score for player {}", player.getScoreboardName(), e);
        }
    }

    private static int getScore(ServerPlayer player, String[] parts, int objIndex) {
        if (parts.length <= objIndex) return 0;
        return getScore(player, parts[objIndex]);
    }

    private static int getScore(ServerPlayer player, String objective) {
        try {
            Scoreboard sb = player.level().getServer().getScoreboard();
            Objective obj = sb.getObjective(objective);
            if (obj == null) return 0;
            var info = sb.getPlayerScoreInfo(ScoreHolder.forNameOnly(player.getScoreboardName()), obj);
            return info != null ? info.value() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static int parseCondInt(String[] parts, int index) {
        if (parts.length <= index) return 0;
        try { return Integer.parseInt(parts[index]); }
        catch (NumberFormatException e) { return 0; }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MenuType<ChestMenu> rowsToType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }
}
