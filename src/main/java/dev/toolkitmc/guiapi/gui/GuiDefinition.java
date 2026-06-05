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
        SET_VAR, ADD_VAR, SUB_VAR, RESET_VAR, CLEAR_VARS, REFRESH, TAKE_ITEM;

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
                default            -> CLOSE;
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
        VAR_EQ, VAR_GT, VAR_LT, VAR_SET, HAS_ITEM, NOT_ITEM;

        public static ConditionType fromString(String s) {
            return switch (s.toLowerCase()) {
                case "has_tag"  -> HAS_TAG;
                case "not_tag"  -> NOT_TAG;
                case "score_gt" -> SCORE_GT;
                case "score_lt" -> SCORE_LT;
                case "score_eq" -> SCORE_EQ;
                case "var_eq"   -> VAR_EQ;
                case "var_gt"   -> VAR_GT;
                case "var_lt"   -> VAR_LT;
                case "var_set"  -> VAR_SET;
                case "has_item" -> HAS_ITEM;
                case "not_item" -> NOT_ITEM;
                default         -> HAS_TAG;
            };
        }
    }

    // ── Records ──────────────────────────────────────────────────────────────

    /**
     * @param type    Action type
     * @param value   Primary value (command, message, sound id, var value, page index…)
     * @param runWith Execution context for run_command
     * @param var     Variable key for set_var / add_var / sub_var / reset_var actions
     */
    public record ButtonAction(ActionType type, String value, RunWith runWith, String var) {
        public ButtonAction(ActionType type, String value) {
            this(type, value, RunWith.PLAYER, "");
        }
        public ButtonAction(ActionType type, String value, RunWith runWith) {
            this(type, value, runWith, "");
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
            Optional<String> itemModelOff
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
            Optional<String> itemModel
    ) {}

    // ── Fields ───────────────────────────────────────────────────────────────

    private final Identifier id;
    private final String title;
    private final int rows;
    private final int pageCount;
    private final List<Button> buttons;
    private final List<ButtonAction> onOpen;
    private final List<ButtonAction> onClose;

    // ── Constructor ──────────────────────────────────────────────────────────

    private GuiDefinition(Identifier id, String title, int rows,
                          List<Button> buttons,
                          List<ButtonAction> onOpen,
                          List<ButtonAction> onClose) {
        this.id        = id;
        this.title     = title;
        this.rows      = rows;
        this.buttons   = buttons;
        this.onOpen    = onOpen;
        this.onClose   = onClose;
        this.pageCount = buttons.stream().mapToInt(Button::page).max().orElse(0) + 1;
    }

    // ── Parser ───────────────────────────────────────────────────────────────

    public static GuiDefinition parse(Identifier id, JsonObject obj) {
        String title = obj.has("title") ? obj.get("title").getAsString() : "GUI";
        int rows = obj.has("rows") ? Math.clamp(obj.get("rows").getAsInt(), 1, 6) : 3;

        List<Button> buttons = new ArrayList<>();
        if (obj.has("buttons") && obj.get("buttons").isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray("buttons")) {
                buttons.add(parseButton(el.getAsJsonObject()));
            }
        }

        List<ButtonAction> onOpen  = parseActionList(obj, "on_open");
        List<ButtonAction> onClose = parseActionList(obj, "on_close");

        return new GuiDefinition(id, title, rows, buttons, onOpen, onClose);
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
                    clickType, condition, List.of(), Optional.of(toggle), Optional.empty(), Optional.empty());
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

        return new Button(slot, page, item, name, lore, glint, clickType, condition, actions, Optional.empty(), customModelData, itemModel);
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

        return new ToggleDefinition(tag, itemOn, itemOff, nameOn, nameOff,
                loreOn, loreOff, glintOn, glintOff, actionsOn, actionsOff,
                customModelDataOn, customModelDataOff, itemModelOn, itemModelOff);
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
        return new ButtonAction(type, value, runWith, var);
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

    /** Returns only buttons belonging to the given page. */
    public List<Button> getButtonsForPage(int page) {
        return buttons.stream().filter(b -> b.page() == page).toList();
    }

    @Override
    public String toString() {
        return "GuiDefinition{id=" + id + ", pages=" + pageCount + ", buttons=" + buttons.size() + "}";
    }
}
