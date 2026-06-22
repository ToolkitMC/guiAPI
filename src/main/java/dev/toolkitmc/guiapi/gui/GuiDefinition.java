package dev.toolkitmc.guiapi.gui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parsed representation of a datapack GUI definition.
 *
 * JSON schema — data/<ns>/gui/<name>.json
 */
public class GuiDefinition {

    // ── Enums ────────────────────────────────────────────────────────────────

    /**
     * Which mouse button triggers this button's actions.
     */
    public enum ContainerType {
        BARREL, PLAYER, CHEST, ENDER_CHEST, CHEST_MINECART;
        
        public static ContainerType fromString(String s) {
            if (s == null) return BARREL;
            return switch (s.toUpperCase()) {
                case "PLAYER"         -> PLAYER;
                case "CHEST"          -> CHEST;
                case "ENDER_CHEST"    -> ENDER_CHEST;
                case "CHEST_MINECART" -> CHEST_MINECART;
                default               -> BARREL;
            };
        }
    }

    public enum ClickType {
        ANY, LEFT, RIGHT, SHIFT;

        public static ClickType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "left"  -> LEFT;
                case "right" -> RIGHT;
                case "shift" -> SHIFT;
                default      -> ANY;
            };
        }
    }

    public enum ActionType {
        RUN_COMMAND, CLOSE, OPEN_GUI, MESSAGE, NEXT_PAGE, PREV_PAGE, GOTO_PAGE, SOUND,
        SET_VAR, ADD_VAR, SUB_VAR, RESET_VAR, CLEAR_VARS, REFRESH, TAKE_ITEM,
        SET_SCORE, ADD_SCORE, SUB_SCORE, ACTION_BAR,
        ADD_EFFECT, REMOVE_EFFECT, CLEAR_EFFECTS, NONE, ANVIL_INPUT;

        public static ActionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "run_command" -> RUN_COMMAND;
                case "close"       -> CLOSE;
                case "open_gui"    -> OPEN_GUI;
                case "message"     -> MESSAGE;
                case "next_page"   -> NEXT_PAGE;
                case "prev_page"   -> PREV_PAGE;
                case "goto_page"   -> GOTO_PAGE;
                case "sound"       -> SOUND;
                case "set_var"     -> SET_VAR;
                case "add_var"     -> ADD_VAR;
                case "sub_var"     -> SUB_VAR;
                case "reset_var"   -> RESET_VAR;
                case "clear_vars"  -> CLEAR_VARS;
                case "refresh"     -> REFRESH;
                case "take_item"   -> TAKE_ITEM;
                case "set_score"   -> SET_SCORE;
                case "add_score"   -> ADD_SCORE;
                case "sub_score"   -> SUB_SCORE;
                case "action_bar"  -> ACTION_BAR;
                case "add_effect"     -> ADD_EFFECT;
                case "remove_effect"  -> REMOVE_EFFECT;
                case "clear_effects"  -> CLEAR_EFFECTS;
                case "none"           -> NONE;
                case "anvil_input"    -> ANVIL_INPUT;
                default            -> NONE;
            };
        }
    }

    public enum RunWith {
        PLAYER, CONSOLE;
        public static RunWith fromString(String s) {
            return "console".equalsIgnoreCase(s) ? CONSOLE : PLAYER;
        }
    }

    public enum ConditionType {
        HAS_TAG, NOT_TAG, SCORE_GT, SCORE_LT, SCORE_EQ,
        VAR_EQ, VAR_GT, VAR_LT, VAR_SET, HAS_ITEM, NOT_ITEM,
        LEVEL_GT, LEVEL_LT, HEALTH_GT, HEALTH_LT, FOOD_GT, FOOD_LT;

        public static ConditionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "has_tag"   -> HAS_TAG;
                case "not_tag"   -> NOT_TAG;
                case "score_gt"  -> SCORE_GT;
                case "score_lt"  -> SCORE_LT;
                case "score_eq"  -> SCORE_EQ;
                case "var_eq"    -> VAR_EQ;
                case "var_gt"    -> VAR_GT;
                case "var_lt"    -> VAR_LT;
                case "var_set"   -> VAR_SET;
                case "has_item"  -> HAS_ITEM;
                case "not_item"  -> NOT_ITEM;
                case "level_gt"  -> LEVEL_GT;
                case "level_lt"  -> LEVEL_LT;
                case "health_gt" -> HEALTH_GT;
                case "health_lt" -> HEALTH_LT;
                case "food_gt"   -> FOOD_GT;
                case "food_lt"   -> FOOD_LT;
                default          -> HAS_TAG;
            };
        }
    }

    // ── Records ──────────────────────────────────────────────────────────────

    /**
     * @param type    Action type
     * @param value   Primary value (command, message, sound id, var value, page index…)
     * @param runWith Execution context for run_command
     * @param var     Variable key for set_var / add_var / sub_var / reset_var actions
     * @param delay   Action execution delay in ticks
     */
    public record ButtonAction(ActionType type, String value, RunWith runWith, String var, int delay) {
        public ButtonAction(ActionType type, String value) {
            this(type, value, RunWith.PLAYER, "", 0);
        }
        public ButtonAction(ActionType type, String value, RunWith runWith) {
            this(type, value, runWith, "", 0);
        }
    }

    public record ButtonCondition(ConditionType type, String value) {}

    /**
     * 1.21.4+ Multi-value Custom Model Data representation
     */
    public record CustomModelDataConfig(
            List<Float> floats,
            List<Boolean> flags,
            List<String> strings,
            List<Integer> colors
    ) {}

    /**
     * Background Filler configuration
     */
    public record FillerConfig(
            String item,
            String name,
            boolean glint,
            boolean hideTooltip
    ) {}

    /**
     * Toggle definition — stored on a button instead of a fixed item/actions.
     * State is tracked via a scoreboard tag on the player.
     */
    public record ToggleDefinition(
            String tag,
            String itemOn,  String itemOff,
            String nameOn,  String nameOff,
            List<String> loreOn, List<String> loreOff,
            boolean glintOn, boolean glintOff,
            List<ButtonAction> actionsOn,
            List<ButtonAction> actionsOff,
            Optional<CustomModelDataConfig> customModelDataOn,
            Optional<CustomModelDataConfig> customModelDataOff,
            Optional<String> itemModelOn,
            Optional<String> itemModelOff,
            String amountOn,
            String amountOff,
            boolean hideTooltipOn,
            boolean hideTooltipOff,
            boolean hideAdditionalTooltipOn,
            boolean hideAdditionalTooltipOff
    ) {}

    /**
     * A button in the GUI.
     * Either {@code toggle} is present (toggle button) or {@code item}/{@code actions} are used.
     */
    public record Button(
            int slot,
            int page,
            String item,
            String name,
            List<String> lore,
            boolean glint,
            ClickType clickType,
            Optional<ButtonCondition> condition,
            List<ButtonAction> actions,
            Optional<ToggleDefinition> toggle,
            Optional<CustomModelDataConfig> customModelData,
            Optional<String> itemModel,
            String amount,
            boolean hideTooltip,
            boolean hideAdditionalTooltip
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final Identifier id;
    private final String title;
    private final int rows;
    private final int pageCount;
    private final List<Button> buttons;
    private final List<ButtonAction> onOpen;
    private final List<ButtonAction> onClose;
    private final Optional<FillerConfig> filler;
    private final int tickRate;
    private final boolean closeOnMove;
    private final ContainerType containerType;

    // ── Constructor ──────────────────────────────────────────────────────────

    public GuiDefinition(Identifier id, String title, int rows,
                          List<Button> buttons,
                          List<ButtonAction> onOpen,
                          List<ButtonAction> onClose,
                          Optional<FillerConfig> filler,
                          int tickRate,
                          boolean closeOnMove) {
        this(id, title, rows, buttons, onOpen, onClose, filler, tickRate, closeOnMove, ContainerType.BARREL);
    }

    public GuiDefinition(Identifier id, String title, int rows,
                          List<Button> buttons,
                          List<ButtonAction> onOpen,
                          List<ButtonAction> onClose,
                          Optional<FillerConfig> filler,
                          int tickRate,
                          boolean closeOnMove,
                          ContainerType containerType) {
        this.id        = id;
        this.title     = title;
        this.rows      = rows;
        this.buttons   = buttons;
        this.onOpen    = onOpen;
        this.onClose   = onClose;
        this.pageCount = buttons.stream().mapToInt(Button::page).max().orElse(0) + 1;
        this.filler    = filler;
        this.tickRate  = tickRate;
        this.closeOnMove = closeOnMove;
        this.containerType = containerType;
    }

    public static GuiDefinition create(Identifier id, String title, int rows,
                                       List<Button> buttons,
                                       List<ButtonAction> onOpen,
                                       List<ButtonAction> onClose,
                                       Optional<FillerConfig> filler,
                                       int tickRate,
                                       boolean closeOnMove) {
        return new GuiDefinition(id, title, rows, buttons, onOpen, onClose, filler, tickRate, closeOnMove, ContainerType.BARREL);
    }

    public static GuiDefinition create(Identifier id, String title, int rows,
                                       List<Button> buttons,
                                       List<ButtonAction> onOpen,
                                       List<ButtonAction> onClose,
                                       Optional<FillerConfig> filler,
                                       int tickRate,
                                       boolean closeOnMove,
                                       ContainerType containerType) {
        return new GuiDefinition(id, title, rows, buttons, onOpen, onClose, filler, tickRate, closeOnMove, containerType);
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    public static GuiDefinition parse(Identifier id, JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "GUI";
        int rows = obj.has("rows") ? Math.clamp(obj.get("rows").getAsInt(), 1, 6) : 3;
        ContainerType containerType = obj.has("container_type")
                ? ContainerType.fromString(obj.get("container_type").getAsString())
                : ContainerType.BARREL;

        List<Button> buttons = new ArrayList<>();
        if (obj.has("buttons") && obj.get("buttons").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("buttons")) {
                buttons.add(parseButton(el.getAsJsonObject()));
            }
        }

        List<ButtonAction> onOpen  = parseActionList(obj, "on_open");
        List<ButtonAction> onClose = parseActionList(obj, "on_close");

        Optional<FillerConfig> filler = Optional.empty();
        if (obj.has("filler") && obj.get("filler").isJsonObject()) {
            JsonObject f = obj.getAsJsonObject("filler");
            String fItem = f.has("item") ? f.get("item").getAsString() : "minecraft:gray_stained_glass_pane";
            String fName = f.has("name") ? f.get("name").getAsString() : " ";
            boolean fGlint = f.has("glint") && f.get("glint").getAsBoolean();
            boolean fHideTooltip = !f.has("hide_tooltip") || f.get("hide_tooltip").getAsBoolean();
            filler = Optional.of(new FillerConfig(fItem, fName, fGlint, fHideTooltip));
        }

        // Fallback to globally configured default tick rate if omitted in JSON
        int tickRate = obj.has("tick_rate") ? obj.get("tick_rate").getAsInt() : dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getDefaultTickRate();
        boolean closeOnMove = obj.has("close_on_move") && obj.get("close_on_move").getAsBoolean();

        return new GuiDefinition(id, title, rows, buttons, onOpen, onClose, filler, tickRate, closeOnMove, containerType);
    }

    private static List<ButtonAction> parseActionList(JsonObject obj, String key) {
        List<ButtonAction> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key))
                list.add(parseAction(el.getAsJsonObject()));
        }
        return list;
    }

    private static Button parseButton(JsonObject b) {
        int slot  = b.has("slot") ? b.get("slot").getAsInt() : 0;
        int page  = b.has("page") ? Math.max(0, b.get("page").getAsInt()) : 0;

        ClickType clickType = b.has("click_type")
                ? ClickType.fromString(b.get("click_type").getAsString())
                : ClickType.ANY;

        Optional<ButtonCondition> condition = Optional.empty();
        if (b.has("condition") && b.get("condition").isJsonObject()) {
            JsonObject c = b.getAsJsonObject("condition");
            ConditionType ct = ConditionType.fromString(
                    c.has("type") ? c.get("type").getAsString() : "has_tag");
            String cv = c.has("value") ? c.get("value").getAsString() : "";
            condition = Optional.of(new ButtonCondition(ct, cv));
        }

        // Toggle button — item/name/lore/actions come from the toggle definition
        if (b.has("toggle") && b.get("toggle").isJsonObject()) {
            ToggleDefinition toggle = parseToggle(b.getAsJsonObject("toggle"));
            return new Button(slot, page, "", "", List.of(), false,
                    clickType, condition, List.of(), Optional.of(toggle), Optional.empty(), Optional.empty(),
                    "1", false, false);
        }

        // Standard button
        String item  = b.has("item") ? b.get("item").getAsString() : "minecraft:stone";
        String name  = b.has("name") ? b.get("name").getAsString() : "";
        boolean glint = b.has("glint") && b.get("glint").getAsBoolean();

        List<String> lore = new ArrayList<>();
        if (b.has("lore") && b.get("lore").isJsonArray()) {
            for (JsonElement l : b.getAsJsonArray("lore"))
                lore.add(l.getAsString());
        }

        List<ButtonAction> actions = new ArrayList<>();
        if (b.has("actions") && b.get("actions").isJsonArray()) {
            for (JsonElement el : b.getAsJsonArray("actions"))
                actions.add(parseAction(el.getAsJsonObject()));
        } else if (b.has("action") && b.get("action").isJsonObject()) {
            actions.add(parseAction(b.getAsJsonObject("action")));
        }
        if (actions.isEmpty()) actions.add(new ButtonAction(ActionType.CLOSE, ""));

        Optional<CustomModelDataConfig> customModelData = parseCustomModelData(b, "custom_model_data");
        Optional<String> itemModel = b.has("item_model")
                ? Optional.of(b.get("item_model").getAsString())
                : Optional.empty();

        String amount = b.has("amount") ? b.get("amount").getAsString() : "1";
        boolean hideTooltip = b.has("hide_tooltip") && b.get("hide_tooltip").getAsBoolean();
        boolean hideAdditionalTooltip = b.has("hide_additional_tooltip") && b.get("hide_additional_tooltip").getAsBoolean();

        return new Button(slot, page, item, name, lore, glint, clickType, condition, actions, Optional.empty(),
                customModelData, itemModel, amount, hideTooltip, hideAdditionalTooltip);
    }

    private static Optional<CustomModelDataConfig> parseCustomModelData(JsonObject obj, String key) {
        if (!obj.has(key)) return Optional.empty();
        JsonElement cmdEl = obj.get(key);
        if (cmdEl.isJsonPrimitive()) {
            try {
                float floatVal = cmdEl.getAsFloat();
                return Optional.of(new CustomModelDataConfig(
                        List.of(floatVal), List.of(), List.of(), List.of()
                ));
            } catch (NumberFormatException ignored) {}
        } else if (cmdEl.isJsonObject()) {
            JsonObject cmdObj = cmdEl.getAsJsonObject();
            List<Float> floats = new ArrayList<>();
            if (cmdObj.has("floats") && cmdObj.get("floats").isJsonArray()) {
                for (JsonElement e : cmdObj.getAsJsonArray("floats")) {
                    floats.add(e.getAsFloat());
                }
            }
            List<Boolean> flags = new ArrayList<>();
            if (cmdObj.has("flags") && cmdObj.get("flags").isJsonArray()) {
                for (JsonElement e : cmdObj.getAsJsonArray("flags")) {
                    flags.add(e.getAsBoolean());
                }
            }
            List<String> strings = new ArrayList<>();
            if (cmdObj.has("strings") && cmdObj.get("strings").isJsonArray()) {
                for (JsonElement e : cmdObj.getAsJsonArray("strings")) {
                    strings.add(e.getAsString());
                }
            }
            List<Integer> colors = new ArrayList<>();
            if (cmdObj.has("colors") && cmdObj.get("colors").isJsonArray()) {
                for (JsonElement e : cmdObj.getAsJsonArray("colors")) {
                    colors.add(e.getAsInt());
                }
            }
            return Optional.of(new CustomModelDataConfig(floats, flags, strings, colors));
        }
        return Optional.empty();
    }

    private static ToggleDefinition parseToggle(JsonObject t) {
        String tag      = t.has("tag")       ? t.get("tag").getAsString()       : "";
        String itemOn   = t.has("item_on")   ? t.get("item_on").getAsString()   : "minecraft:lime_dye";
        String itemOff  = t.has("item_off")  ? t.get("item_off").getAsString()  : "minecraft:gray_dye";
        String nameOn   = t.has("name_on")   ? t.get("name_on").getAsString()   : "§aEnabled";
        String nameOff  = t.has("name_off")  ? t.get("name_off").getAsString()  : "§7Disabled";
        boolean glintOn  = t.has("glint_on")  && t.get("glint_on").getAsBoolean();
        boolean glintOff = t.has("glint_off") && t.get("glint_off").getAsBoolean();

        List<String> loreOn  = parseStringList(t, "lore_on");
        List<String> loreOff = parseStringList(t, "lore_off");

        List<ButtonAction> actionsOn  = new ArrayList<>();
        List<ButtonAction> actionsOff = new ArrayList<>();

        if (t.has("actions_on") && t.get("actions_on").isJsonArray())
            for (JsonElement el : t.getAsJsonArray("actions_on"))
                actionsOn.add(parseAction(el.getAsJsonObject()));

        if (t.has("actions_off") && t.get("actions_off").isJsonArray())
            for (JsonElement el : t.getAsJsonArray("actions_off"))
                actionsOff.add(parseAction(el.getAsJsonObject()));

        // Default: toggle the tag
        if (actionsOn.isEmpty())
            actionsOn.add(new ButtonAction(ActionType.RUN_COMMAND, "tag @s remove " + tag, RunWith.CONSOLE));
        if (actionsOff.isEmpty())
            actionsOff.add(new ButtonAction(ActionType.RUN_COMMAND, "tag @s add " + tag, RunWith.CONSOLE));

        Optional<CustomModelDataConfig> customModelDataOn  = parseCustomModelData(t, "custom_model_data_on");
        Optional<CustomModelDataConfig> customModelDataOff = parseCustomModelData(t, "custom_model_data_off");

        Optional<String> itemModelOn  = t.has("item_model_on")  ? Optional.of(t.get("item_model_on").getAsString())  : Optional.empty();
        Optional<String> itemModelOff = t.has("item_model_off") ? Optional.of(t.get("item_model_off").getAsString()) : Optional.empty();

        String amountOn  = t.has("amount_on")  ? t.get("amount_on").getAsString()  : "1";
        String amountOff = t.has("amount_off") ? t.get("amount_off").getAsString() : "1";

        boolean hideTooltipOn  = t.has("hide_tooltip_on")  && t.get("hide_tooltip_on").getAsBoolean();
        boolean hideTooltipOff = t.has("hide_tooltip_off") && t.get("hide_tooltip_off").getAsBoolean();

        boolean hideAdditionalTooltipOn  = t.has("hide_additional_tooltip_on")  && t.get("hide_additional_tooltip_on").getAsBoolean();
        boolean hideAdditionalTooltipOff = t.has("hide_additional_tooltip_off") && t.get("hide_additional_tooltip_off").getAsBoolean();

        return new ToggleDefinition(tag, itemOn, itemOff, nameOn, nameOff,
                loreOn, loreOff, glintOn, glintOff, actionsOn, actionsOff,
                customModelDataOn, customModelDataOff, itemModelOn, itemModelOff,
                amountOn, amountOff, hideTooltipOn, hideTooltipOff, hideAdditionalTooltipOn, hideAdditionalTooltipOff);
    }

    private static List<String> parseStringList(JsonObject obj, String key) {
        List<String> list = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray())
            for (JsonElement el : obj.getAsJsonArray(key))
                list.add(el.getAsString());
        return list;
    }

    private static ButtonAction parseAction(JsonObject a) {
        ActionType type = ActionType.fromString(
                a.has("type") ? a.get("type").getAsString() : "close");
        String value    = a.has("value")   ? a.get("value").getAsString()   : "";
        String var      = a.has("var")     ? a.get("var").getAsString()     : "";
        RunWith runWith = a.has("run_with")
                ? RunWith.fromString(a.get("run_with").getAsString())
                : RunWith.PLAYER;
        int delay = a.has("delay") ? Math.max(0, a.get("delay").getAsInt()) : 0;
        return new ButtonAction(type, value, runWith, var, delay);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public Identifier getId()              { return id; }
    public String getTitle()               { return title; }
    /** Always in [1, 6]. */
    public int getRows()                   { return Math.clamp(rows, 1, 6); }
    public int getPageCount()              { return pageCount; }
    public List<Button> getButtons()       { return buttons; }
    public List<ButtonAction> getOnOpen()  { return onOpen; }
    public List<ButtonAction> getOnClose() { return onClose; }
    public Optional<FillerConfig> getFiller() { return filler; }
    public int getTickRate()               { return tickRate; }
    public boolean isCloseOnMove()         { return closeOnMove; }
    public ContainerType getContainerType() { return containerType; }

    /** Returns only buttons belonging to the given page. */
    public List<Button> getButtonsForPage(int page) {
        return buttons.stream().filter(b -> b.page() == page).toList();
    }

    @Override
    public String toString() {
        return "GuiDefinition{id=" + id + ", pages=" + pageCount + ", buttons=" + buttons.size() + "}";
    }
}
