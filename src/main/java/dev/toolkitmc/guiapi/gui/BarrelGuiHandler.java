package dev.toolkitmc.guiapi.gui;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.scoreboard.ScoreHolder;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

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

        PlayerTickState(GuiDefinition def, int page, ServerPlayerEntity player) {
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

    public static void open(ServerPlayerEntity player, GuiDefinition def) {
        open(player, def, 0);
    }

    public static void open(ServerPlayerEntity player, GuiDefinition def, int page) {
        page = Math.clamp(page, 0, def.getPageCount() - 1);
        int rows = Math.clamp(def.getRows(), 1, 6);
        int finalPage = page;

        // Register state BEFORE building inventory so that any handleClick call
        // triggered during screen open (edge case) already sees the correct state.
        OPEN_GUIS.put(player.getUuid(), new PlayerTickState(def, page, player));
        debug("open: player={} gui={} page={}", player.getNameForScoreboard(), def.getId(), page);
        SimpleInventory inv = buildInventory(player, def, page, rows * 9);

        String resolvedTitle = resolve(def.getTitle(), player, def, page);

        player.openHandledScreen(new NamedScreenHandlerFactory() {
            @Override
            public Text getDisplayName() {
                return Text.literal(resolvedTitle);
            }

            @Override
            public net.minecraft.screen.ScreenHandler createMenu(
                    int syncId, PlayerInventory playerInv, PlayerEntity p) {
                return new GuiScreenHandler(rowsToType(rows), syncId, playerInv, inv, rows, def, finalPage);
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
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);

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
                    player.closeHandledScreen();
                    player.sendMessage(Text.literal("§cGUI closed due to movement!"), true);
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

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.playerUuid);
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

    public static void refreshCurrentGui(ServerPlayerEntity player) {
        if (player.currentScreenHandler instanceof GuiScreenHandler guiHandler) {
            GuiDefinition def = guiHandler.getDefinition();
            int page = guiHandler.getPage();
            int rows = guiHandler.getRows();
            int size = rows * 9;
            Inventory inv = guiHandler.getInventory();

            // Clear inventory first
            for (int slot = 0; slot < size; slot++) {
                inv.setStack(slot, ItemStack.EMPTY);
            }

            // Populate according to page buttons and conditions
            for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
                if (btn.slot() < 0 || btn.slot() >= size) continue;
                if (!evaluateCondition(player, btn)) continue;
                inv.setStack(btn.slot(), buildStack(player, def, page, btn));
            }

            // Apply background filler for empty slots
            if (def.getFiller().isPresent()) {
                GuiDefinition.FillerConfig fill = def.getFiller().get();
                ItemStack fillStack = buildFillerStack(fill);
                for (int slot = 0; slot < size; slot++) {
                    if (inv.getStack(slot).isEmpty()) {
                        inv.setStack(slot, fillStack.copy());
                    }
                }
            }

            // Send content updates to client
            guiHandler.sendContentUpdates();
            debug("refreshCurrentGui: player={} gui={} page={}", player.getNameForScoreboard(), def.getId(), page);
        }
    }

    public static boolean handleClick(ServerPlayerEntity player, GuiDefinition def,
                                      int page, int slot, int mouseButton, SlotActionType actionType) {
        // mouseButton: 0 = left, 1 = right (Minecraft protocol)
        final boolean isShift = actionType == SlotActionType.QUICK_MOVE;
        final boolean isLeft  = !isShift && mouseButton == 0 && actionType == SlotActionType.PICKUP;
        final boolean isRight = !isShift && mouseButton == 1 && actionType == SlotActionType.PICKUP;

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

            boolean isToggle = btn.toggle().isPresent();
            List<GuiDefinition.ButtonAction> actions = resolveActions(player, btn);

            if (isToggle) {
                GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
                List<GuiDefinition.ButtonAction> toggleActions = resolveActions(player, btn);

                // Flip tag synchronously via Java API
                boolean wasOn = player.getCommandTags().contains(tgl.tag());
                if (wasOn) player.removeCommandTag(tgl.tag());
                else       player.addCommandTag(tgl.tag());

                // Execute all defined actions using Delayed Action Chain Engine
                executeDelayedActionChain(player, def, page, toggleActions, 0, false);
                return true;
            }

            // Execute standard click action chain using Delayed Action Chain Engine
            executeDelayedActionChain(player, def, page, actions, 0, false);
            return true;
        }
        return true;
    }

    public static void executeDelayedActionChain(ServerPlayerEntity player, GuiDefinition def, int page, List<GuiDefinition.ButtonAction> actions, int startIndex, boolean ignoreFirstDelay) {
        for (int i = startIndex; i < actions.size(); i++) {
            GuiDefinition.ButtonAction action = actions.get(i);
            boolean checkDelay = (i > startIndex) || !ignoreFirstDelay;
            if (checkDelay && action.delay() > 0 && dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowDelayedActions()) {
                // Schedule remaining actions starting from this delayed one!
                PENDING_TASKS.add(new DelayedTask(player.getUuid(), actions, i, action.delay(), def, page));
                break;
            }
            boolean shouldBreak = executeAction(player, def, page, action);
            if (shouldBreak) break;
        }
    }

    public static void onClose(UUID playerUuid) {
        if (OPEN_GUIS.remove(playerUuid) != null) {
            GuiVarStore.INSTANCE.clear(playerUuid);
        }
    }

    /**
     * Called when the player genuinely closes the GUI (ESC, etc.).
     * Fires on_close actions and clears runtime variables.
     */
    public static void onClose(ServerPlayerEntity player) {
        PlayerTickState state = OPEN_GUIS.remove(player.getUuid());
        if (state == null) return;
        debug("close: player={} gui={}", player.getNameForScoreboard(), state.def.getId());
        for (GuiDefinition.ButtonAction action : state.def.getOnClose()) {
            executeAction(player, state.def, state.page, action);
        }
        GuiVarStore.INSTANCE.clear(player.getUuid());
    }

    /**
     * Called internally before navigating to another GUI or page.
     * Removes open state WITHOUT clearing runtime variables.
     */
    private static void navigateAway(ServerPlayerEntity player) {
        OPEN_GUIS.remove(player.getUuid());
    }

    // ── Inventory builder ────────────────────────────────────────────────────

    private static SimpleInventory buildInventory(ServerPlayerEntity player,
                                                  GuiDefinition def, int page, int size) {
        SimpleInventory inv = new SimpleInventory(size) {
            @Override public boolean canPlayerUse(PlayerEntity p) { return true; }
        };

        for (GuiDefinition.Button btn : def.getButtonsForPage(page)) {
            if (btn.slot() < 0 || btn.slot() >= size) continue;
            if (!evaluateCondition(player, btn)) continue;
            inv.setStack(btn.slot(), buildStack(player, def, page, btn));
        }

        // Apply background filler for empty slots
        if (def.getFiller().isPresent()) {
            GuiDefinition.FillerConfig fill = def.getFiller().get();
            ItemStack fillStack = buildFillerStack(fill);
            for (int slot = 0; slot < size; slot++) {
                if (inv.getStack(slot).isEmpty()) {
                    inv.setStack(slot, fillStack.copy());
                }
            }
        }

        return inv;
    }

    private static ItemStack buildFillerStack(GuiDefinition.FillerConfig fill) {
        Identifier id = Identifier.tryParse(fill.item());
        Item item = Items.STONE;
        if (id != null && Registries.ITEM.containsId(id)) {
            item = Registries.ITEM.get(id);
        }
        ItemStack stack = new ItemStack(item);
        if (!fill.name().isEmpty()) {
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(fill.name()).styled(s -> s.withItalic(false)));
        }
        if (fill.glint()) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));
        if (fill.hideTooltip()) {
            // Pristine fix: Match Java 21 SequencedSet for empty list
            net.minecraft.component.type.TooltipDisplayComponent tooltipDisplay =
                    new net.minecraft.component.type.TooltipDisplayComponent(false, new java.util.LinkedHashSet<>());
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        }
        return stack;
    }

    private static ItemStack buildStack(ServerPlayerEntity player,
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
            boolean on = player.getCommandTags().contains(tgl.tag());
            itemId = on ? tgl.itemOn()  : tgl.itemOff();
            name   = on ? tgl.nameOn()  : tgl.nameOff();
            lore   = on ? tgl.loreOn()  : tgl.loreOff();
            glint  = on ? tgl.glintOn() : tgl.glintOff();
            customModelData = on ? tgl.customModelDataOn() : tgl.customModelDataOff();
            itemModel = on ? tgl.itemModelOn() : tgl.itemModelOff();
            amountStr = on ? tgl.amountOn() : tgl.amountOff();
            hideTooltip = on ? tgl.hideTooltipOn() : tgl.hideTooltipOff();
            hideAdditionalTooltip = on ? tgl.hideAdditionalTooltipOn() : tgl.hideAdditionalTooltipOff();
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

        Identifier id = Identifier.tryParse(itemId);
        Item item;
        if (id != null && Registries.ITEM.containsId(id)) {
            item = Registries.ITEM.get(id);
        } else {
            if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogUnknownItems())
                GuiApiMod.LOGGER.warn("[GuiAPI] Unknown item '{}' in slot {}, falling back to stone.", itemId, btn.slot());
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
            stack.set(DataComponentTypes.CUSTOM_NAME,
                    Text.literal(resolvedName).styled(s -> s.withItalic(false)));
        }

        if (!lore.isEmpty()) {
            List<Text> loreTexts = lore.stream()
                    .map(l -> (Text) Text.literal(resolve(l, player, def, page))
                            .styled(s -> s.withItalic(false)))
                    .toList();
            stack.set(DataComponentTypes.LORE, new LoreComponent(loreTexts));
        }

        if (glint) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);

        // Mark as GUI item to block extraction
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(new NbtCompound()));

        // --- Custom Model Data ---
        if (customModelData.isPresent()) {
            GuiDefinition.CustomModelDataConfig cmd = customModelData.get();
            net.minecraft.component.type.CustomModelDataComponent component =
                    new net.minecraft.component.type.CustomModelDataComponent(
                            cmd.floats(), cmd.flags(), cmd.strings(), cmd.colors()
                    );
            stack.set(DataComponentTypes.CUSTOM_MODEL_DATA, component);
        }

        // --- Item Model ---
        if (itemModel.isPresent()) {
            Identifier modelId = Identifier.tryParse(itemModel.get());
            if (modelId != null) {
                stack.set(DataComponentTypes.ITEM_MODEL, modelId);
            }
        }

        // --- Tooltip Control (1.21.4+ TOOLTIP_DISPLAY Component) ---
        if (hideTooltip) {
            // Pristine fix: Match Java 21 SequencedSet for empty list
            net.minecraft.component.type.TooltipDisplayComponent tooltipDisplay =
                    new net.minecraft.component.type.TooltipDisplayComponent(false, new java.util.LinkedHashSet<>());
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        } else if (hideAdditionalTooltip) {
            // Pristine fix: Match Java 21 SequencedSet constructor parameter
            net.minecraft.component.type.TooltipDisplayComponent tooltipDisplay =
                    new net.minecraft.component.type.TooltipDisplayComponent(true, new java.util.LinkedHashSet<>(java.util.List.of(
                            DataComponentTypes.ATTRIBUTE_MODIFIERS,
                            DataComponentTypes.ENCHANTMENTS,
                            DataComponentTypes.STORED_ENCHANTMENTS,
                            DataComponentTypes.DYED_COLOR,
                            DataComponentTypes.POTION_CONTENTS,
                            DataComponentTypes.UNBREAKABLE
                    )));
            stack.set(DataComponentTypes.TOOLTIP_DISPLAY, tooltipDisplay);
        }

        return stack;
    }

    // ── Placeholder resolution ────────────────────────────────────────────────

    static String resolve(String text, ServerPlayerEntity player,
                          GuiDefinition def, int page) {
        if (text == null || text.isEmpty() || !text.contains("{")) return text;

        text = text.replace("{player}", player.getDisplayName().getString());
        text = text.replace("{gui}",    def.getId().toString());
        text = text.replace("{page}",   String.valueOf(page));
        text = text.replace("{page1}",  String.valueOf(page + 1));
        text = text.replace("{pages}",  String.valueOf(def.getPageCount()));

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
            String val = GuiVarStore.INSTANCE.getOrDefault(player.getUuid(), key, "");
            text = text.substring(0, idx) + val + text.substring(end + 1);
        }

        debug("resolve: \"{}\" → \"{}\"", text.length() > 60 ? text.substring(0, 60) + "..." : text, text);
        return text;
    }

    // ── Condition evaluation ─────────────────────────────────────────────────

    static boolean evaluateCondition(ServerPlayerEntity player, GuiDefinition.Button btn) {
        if (btn.condition().isEmpty()) return true;

        GuiDefinition.ButtonCondition cond = btn.condition().get();
        return switch (cond.type()) {
            case HAS_TAG  -> player.getCommandTags().contains(cond.value());
            case NOT_TAG  -> !player.getCommandTags().contains(cond.value());
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
                        .getOrDefault(player.getUuid(), p[0], "").equals(p[1]);
            }
            case VAR_GT   -> {
                String[] p = cond.value().split(":", 2);
                yield p.length == 2 && GuiVarStore.INSTANCE
                        .getInt(player.getUuid(), p[0]) > parseIntSafe(p[1]);
            }
            case VAR_LT   -> {
                String[] p = cond.value().split(":", 2);
                yield p.length == 2 && GuiVarStore.INSTANCE
                        .getInt(player.getUuid(), p[0]) < parseIntSafe(p[1]);
            }
            case VAR_SET  -> GuiVarStore.INSTANCE.get(player.getUuid(), cond.value()) != null;
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
            case FOOD_GT -> player.getHungerManager().getFoodLevel() > parseIntSafe(cond.value());
            case FOOD_LT -> player.getHungerManager().getFoodLevel() < parseIntSafe(cond.value());
        };
    }

    private static boolean hasItemCount(ServerPlayerEntity player, String itemId, int requiredAmount) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return false;
        Item targetItem = Registries.ITEM.get(id);

        int count = 0;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                count += stack.getCount();
            }
        }
        return count >= requiredAmount;
    }

    private static void takeItemCount(ServerPlayerEntity player, String itemId, int amountToTake) {
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return;
        Item targetItem = Registries.ITEM.get(id);

        int remaining = amountToTake;
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(targetItem)) {
                int count = stack.getCount();
                if (count <= remaining) {
                    player.getInventory().setStack(i, ItemStack.EMPTY);
                    remaining -= count;
                } else {
                    stack.decrement(remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
        }
        // Sync player inventory with client
        player.currentScreenHandler.sendContentUpdates();
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private static float parseFloatSafe(String s) {
        try { return Float.parseFloat(s); } catch (NumberFormatException e) { return 0.0f; }
    }

    // ── Toggle action resolution ─────────────────────────────────────────────

    private static List<GuiDefinition.ButtonAction> resolveActions(
            ServerPlayerEntity player, GuiDefinition.Button btn) {
        if (btn.toggle().isPresent()) {
            GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
            boolean on = player.getCommandTags().contains(tgl.tag());
            return on ? tgl.actionsOn() : tgl.actionsOff();
        }
        return btn.actions();
    }

    // ── Action execution ─────────────────────────────────────────────────────

    static boolean executeAction(ServerPlayerEntity player, GuiDefinition def,
                                 int currentPage, GuiDefinition.ButtonAction action) {
        MinecraftServer server = player.getServer();
        debug("action: player={} type={} value=\"{}\"",
                player.getNameForScoreboard(), action.type(), action.value());
        switch (action.type()) {
            case RUN_COMMAND -> {
                String cmd = action.value().startsWith("/")
                        ? action.value().substring(1) : action.value();
                cmd = resolve(cmd, player, def, currentPage);
                // Auditing executed command logs if enabled globally in config
                if (dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isLogCommands()) {
                    GuiApiMod.LOGGER.info("[GuiAPI|CommandLog] Player {} executed command: {}", player.getNameForScoreboard(), cmd);
                }
                if (action.runWith() == GuiDefinition.RunWith.CONSOLE) {
                    if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowConsoleRunWith()) {
                        GuiApiMod.LOGGER.warn("[GuiAPI] run_with:console is disabled in config. Skipping: {}", cmd);
                        break;
                    }
                    server.getCommandManager().executeWithPrefix(server.getCommandSource(), cmd);
                } else {
                    server.getCommandManager().executeWithPrefix(player.getCommandSource(), cmd);
                }
            }
            case CLOSE -> {
                player.closeHandledScreen();
                return true;
            }
            case OPEN_GUI -> {
                navigateAway(player);
                player.closeHandledScreen();
                Identifier targetId = Identifier.tryParse(action.value());
                if (targetId != null) {
                    dev.toolkitmc.guiapi.loader.GuiRegistry.INSTANCE
                            .get(targetId)
                            .ifPresentOrElse(
                                    target -> open(player, target),
                                    () -> player.sendMessage(
                                            Text.literal("[GuiAPI] GUI not found: " + targetId), false));
                }
                return true;
            }
            case MESSAGE -> player.sendMessage(
                    Text.literal(resolve(action.value(), player, def, currentPage)), false);
            case NEXT_PAGE -> {
                int next = currentPage + 1;
                if (next < def.getPageCount()) {
                    navigateAway(player);
                    player.closeHandledScreen();
                    open(player, def, next);
                }
                return true;
            }
            case PREV_PAGE -> {
                int prev = currentPage - 1;
                if (prev >= 0) {
                    navigateAway(player);
                    player.closeHandledScreen();
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
                    net.minecraft.sound.SoundEvent soundEvent =
                            Registries.SOUND_EVENT.get(soundIdent);
                    if (soundEvent != null) {
                        player.playSoundToPlayer(soundEvent,
                                net.minecraft.sound.SoundCategory.PLAYERS, volume, pitch);
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
                        player.closeHandledScreen();
                        open(player, def, target);
                    }
                } catch (NumberFormatException ignored) {}
                return true;
            }
            case SET_VAR -> {
                if (!action.var().isEmpty()) {
                    String resolved = resolve(action.value(), player, def, currentPage);
                    GuiVarStore.INSTANCE.set(player.getUuid(), action.var(), resolved);
                }
            }
            case ADD_VAR -> {
                if (!action.var().isEmpty()) {
                    try {
                        int delta = Integer.parseInt(resolve(action.value(), player, def, currentPage));
                        GuiVarStore.INSTANCE.add(player.getUuid(), action.var(), delta);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case SUB_VAR -> {
                if (!action.var().isEmpty()) {
                    try {
                        int delta = Integer.parseInt(resolve(action.value(), player, def, currentPage));
                        GuiVarStore.INSTANCE.add(player.getUuid(), action.var(), -delta);
                    } catch (NumberFormatException ignored) {}
                }
            }
            case RESET_VAR -> {
                if (!action.var().isEmpty()) {
                    GuiVarStore.INSTANCE.remove(player.getUuid(), action.var());
                }
            }
            case CLEAR_VARS -> GuiVarStore.INSTANCE.clear(player.getUuid());
            case REFRESH -> refreshCurrentGui(player);
            case TAKE_ITEM -> {
                String[] parts = action.value().split(":", 2);
                String itemId = parts[0];
                int amount = parts.length > 1 ? parseIntSafe(parts[1]) : 1;
                takeItemCount(player, itemId, amount);
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
                player.sendMessage(Text.literal(resolved), true);
            }
            case ADD_EFFECT -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    break;
                }
                String resolved = resolve(action.value(), player, def, currentPage);
                String[] parts = resolved.split(":");
                if (parts.length >= 2) {
                    try {
                        String effectId = parts[0];
                        int durationSecs = Integer.parseInt(parts[1]);
                        int amp = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                        boolean particles = parts.length <= 3 || Boolean.parseBoolean(parts[3]);

                        Identifier effIdent = Identifier.tryParse(effectId);
                        if (effIdent != null && Registries.STATUS_EFFECT.containsId(effIdent)) {
                            net.minecraft.entity.effect.StatusEffect effect = Registries.STATUS_EFFECT.get(effIdent);
                            if (effect != null) {
                                player.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(
                                        Registries.STATUS_EFFECT.getEntry(effect), durationSecs * 20, amp, false, particles, particles
                                ));
                            }
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
            case REMOVE_EFFECT -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    break;
                }
                String resolved = resolve(action.value(), player, def, currentPage);
                Identifier effIdent = Identifier.tryParse(resolved);
                if (effIdent != null && Registries.STATUS_EFFECT.containsId(effIdent)) {
                    net.minecraft.entity.effect.StatusEffect effect = Registries.STATUS_EFFECT.get(effIdent);
                    if (effect != null) {
                        player.removeStatusEffect(Registries.STATUS_EFFECT.getEntry(effect));
                    }
                }
            }
            case CLEAR_EFFECTS -> {
                if (!dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.isAllowStatusEffects()) {
                    GuiApiMod.LOGGER.warn("[GuiAPI] Status effects are disabled globally in config!");
                    break;
                }
                player.clearStatusEffects();
            }
        }
        return false;
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

    private static void modifyScore(ServerPlayerEntity player, String objectiveName, int val, ScoreModType modType) {
        try {
            Scoreboard sb = player.getServer().getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective(objectiveName);
            if (obj == null) return;
            net.minecraft.scoreboard.ScoreAccess score = sb.getOrCreateScore(ScoreHolder.fromName(player.getNameForScoreboard()), obj);
            if (score != null) {
                int current = score.getScore();
                int target = switch (modType) {
                    case SET -> val;
                    case ADD -> current + val;
                    case SUB -> current - val;
                };
                score.setScore(target);
            }
        } catch (Exception e) {
            GuiApiMod.LOGGER.error("[GuiAPI] Failed to modify score for player {}", player.getNameForScoreboard(), e);
        }
    }

    private static int getScore(ServerPlayerEntity player, String[] parts, int objIndex) {
        if (parts.length <= objIndex) return 0;
        return getScore(player, parts[objIndex]);
    }

    private static int getScore(ServerPlayerEntity player, String objective) {
        try {
            Scoreboard sb = player.getServer().getScoreboard();
            ScoreboardObjective obj = sb.getNullableObjective(objective);
            if (obj == null) return 0;
            var score = sb.getScore(ScoreHolder.fromName(player.getNameForScoreboard()), obj);
            return score != null ? score.getScore() : 0;
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

    private static ScreenHandlerType<GenericContainerScreenHandler> rowsToType(int rows) {
        return switch (rows) {
            case 1 -> ScreenHandlerType.GENERIC_9X1;
            case 2 -> ScreenHandlerType.GENERIC_9X2;
            case 3 -> ScreenHandlerType.GENERIC_9X3;
            case 4 -> ScreenHandlerType.GENERIC_9X4;
            case 5 -> ScreenHandlerType.GENERIC_9X5;
            default -> ScreenHandlerType.GENERIC_9X6;
        };
    }
}
