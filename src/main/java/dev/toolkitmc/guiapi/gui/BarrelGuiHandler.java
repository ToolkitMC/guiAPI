package dev.toolkitmc.guiapi.gui;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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

/**
 * Server-side chest GUI handler.
 *
 * Features:
 *  - Multi-page support (page tracked per player)
 *  - Toggle buttons (tag-backed on/off state)
 *  - Conditional buttons (has_tag, not_tag, score_gt/lt/eq)
 *  - Multiple actions per button (executed in order)
 *  - on_open / on_close action hooks
 *  - Placeholder substitution in text fields
 *  - Enchantment glint on items
 *  - run_command with run_with: player|console
 */
public class BarrelGuiHandler {

    /** Player UUID → currently open GUI state */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, OpenState> OPEN_GUIS =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record OpenState(GuiDefinition def, int page) {}

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
        OPEN_GUIS.put(player.getUuid(), new OpenState(def, page));
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
        for (GuiDefinition.ButtonAction action : def.getOnOpen()) {
            boolean stop = executeAction(player, def, page, action);
            if (stop) break;
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
                // Resolve the correct action list BEFORE flipping the tag,
                // so actionsOn fires when the toggle was ON (turning it OFF), etc.
                List<GuiDefinition.ButtonAction> toggleActions = resolveActions(player, btn);

                // Flip tag synchronously via Java API — avoids command dispatcher race.
                boolean wasOn = player.getCommandTags().contains(tgl.tag());
                if (wasOn) player.removeCommandTag(tgl.tag());
                else       player.addCommandTag(tgl.tag());

                // Execute all defined actions. Default tag commands were already
                // applied above; run everything else (sounds, messages, commands…).
                boolean chainBroken = false;
                for (GuiDefinition.ButtonAction action : toggleActions) {
                    boolean shouldBreak = executeAction(player, def, page, action);
                    if (shouldBreak) { chainBroken = true; break; }
                }
                if (!chainBroken) {
                    navigateAway(player);
                    player.closeHandledScreen();
                    open(player, def, page);
                }
                return true;
            }

            for (GuiDefinition.ButtonAction action : actions) {
                boolean shouldBreak = executeAction(player, def, page, action);
                if (shouldBreak) break;
            }
            return true;
        }
        return true;
    }

    public static void onClose(UUID playerUuid) {
        // UUID-only overload: fires when we don't have a ServerPlayerEntity reference.
        // Cannot run on_close actions (no player object) but MUST clear vars to avoid leak.
        if (OPEN_GUIS.remove(playerUuid) != null) {
            GuiVarStore.INSTANCE.clear(playerUuid);
        }
    }

    /**
     * Called when the player genuinely closes the GUI (ESC, etc.).
     * Fires on_close actions and clears runtime variables.
     */
    public static void onClose(ServerPlayerEntity player) {
        OpenState state = OPEN_GUIS.remove(player.getUuid());
        if (state == null) return;
        debug("close: player={} gui={}", player.getNameForScoreboard(), state.def().getId());
        for (GuiDefinition.ButtonAction action : state.def().getOnClose()) {
            executeAction(player, state.def(), state.page(), action);
        }
        // Only clear vars on a real close, not on GUI navigation (open_gui, next/prev_page).
        // Navigation removes the state via navigateAway() before calling closeHandledScreen().
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

        return inv;
    }

    private static ItemStack buildStack(ServerPlayerEntity player,
                                        GuiDefinition def, int page,
                                        GuiDefinition.Button btn) {
        // Resolve toggle state to concrete display values
        final String  itemId;
        final String  name;
        final List<String> lore;
        final boolean glint;

        if (btn.toggle().isPresent()) {
            GuiDefinition.ToggleDefinition tgl = btn.toggle().get();
            boolean on = player.getCommandTags().contains(tgl.tag());
            itemId = on ? tgl.itemOn()  : tgl.itemOff();
            name   = on ? tgl.nameOn()  : tgl.nameOff();
            lore   = on ? tgl.loreOn()  : tgl.loreOff();
            glint  = on ? tgl.glintOn() : tgl.glintOff();
        } else {
            itemId = btn.item();
            name   = btn.name();
            lore   = btn.lore();
            glint  = btn.glint();
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

        ItemStack stack = new ItemStack(item);

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

        return stack;
    }

    // ── Placeholder resolution ────────────────────────────────────────────────

    /**
     * Resolves placeholders in a string:
     *   {player}    — player name
     *   {gui}       — GUI id
     *   {page}      — page index (0-based)
     *   {page1}     — page index (1-based)
     *   {pages}     — total page count
     *   {score:obj} — player score in objective "obj"
     */
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
        };
    }

    private static int parseIntSafe(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    // ── Toggle action resolution ─────────────────────────────────────────────

    /**
     * Returns the actions to execute for a click, accounting for toggle state.
     */
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

    /**
     * Execute a single action.
     * @return true if the action chain should stop (screen was closed/changed)
     */
    static boolean executeAction(ServerPlayerEntity player, GuiDefinition def,
                                 int currentPage, GuiDefinition.ButtonAction action) {
        MinecraftServer server = player.getServer();
        debug("action: player={} type={} value=\"{}\"",
                player.getNameForScoreboard(), action.type(), action.value());
        switch (action.type()) {
            case RUN_COMMAND -> {
                String cmd = action.value().startsWith("/")
                        ? action.value().substring(1) : action.value();
                // Resolve placeholders in command value too
                cmd = resolve(cmd, player, def, currentPage);
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
                // value format: "minecraft:sound.id"  (volume=1.0, pitch=1.0)
                //           or: "minecraft:sound.id:volume:pitch"
                String[] parts = action.value().split(":");
                // Reconstruct namespace:path which may itself contain ':'
                // Format is always <namespace>:<path>[:<volume>[:<pitch>]]
                // So minimum 2 parts (namespace + path), max 4.
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
                    soundId = action.value();
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
        }
        return false;
    }

    // ── Score helpers ─────────────────────────────────────────────────────────

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
