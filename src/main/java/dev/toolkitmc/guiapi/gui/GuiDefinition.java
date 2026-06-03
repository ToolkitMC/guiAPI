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
 * JSON schema — data/<ns>/gui/<name>.json:
 * {
 *   "title": "My GUI",
 *   "rows": 3,
 *   "on_open":  [ { "type": "run_command", "value": "..." } ],  // optional
 *   "on_close": [ { "type": "run_command", "value": "..." } ],  // optional
 *   "buttons": [
 *     {
 *       "slot": 4,
 *       "page": 0,                        // optional, default 0
 *       "item": "minecraft:diamond",
 *       "name": "§bClick Me",             // supports {player}, {gui}, {page}
 *       "lore": ["§7Score: {score:coins}"],
 *       "glint": true,
 *       "click_type": "any",             // any (default) | left | right | shift
 *       "condition": {
 *         "type": "has_tag",             // has_tag | not_tag | score_gt | score_lt | score_eq
 *         "value": "my_tag"
 *       },
 *       "actions": [
 *         { "type": "run_command", "value": "/say hi", "run_with": "player" },
 *         { "type": "close" }
 *       ]
 *     },
 *     {
 *       "slot": 8,
 *       "toggle": {
 *         "tag": "my_toggle_tag",         // scoreboard tag used as toggle state
 *         "item_on":  "minecraft:lime_dye",
 *         "item_off": "minecraft:gray_dye",
 *         "name_on":  "§aEnabled",
 *         "name_off": "§7Disabled",
 *         "lore_on":  ["§7Click to disable."],
 *         "lore_off": ["§7Click to enable."],
 *         "glint_on":  false,
 *         "glint_off": false,
 *         "actions_on":  [ { "type": "run_command", "value": "tag @s remove my_toggle_tag" } ],
 *         "actions_off": [ { "type": "run_command", "value": "tag @s add my_toggle_tag" } ]
 *       }
 *     }
 *   ]
 * }
 *
 * Placeholders (resolved at render time per-player):
 *   {player}        — player's display name
 *   {gui}           — GUI ID (namespace:name)
 *   {page}          — current page index (0-based)
 *   {page1}         — current page index (1-based)
 *   {pages}         — total page count
 *   {score:obj}     — player's score in objective "obj"
 *
 * Action types:
 *   run_command  — run a command (run_with: player|console)
 *   close        — close the GUI
 *   open_gui     — open another GUI by id (value: "ns:name")
 *   message      — send chat message to player (value: text)
 *   next_page    — go to next page
 *   prev_page    — go to previous page
 *   goto_page    — go to specific page (value: page index as string)
 *
 * Condition types:
 *   has_tag      — value: tag name. Visible if player has tag.
 *   not_tag      — value: tag name. Visible if player does NOT have tag.
 *   score_gt     — value: "objective:threshold". Visible if score > threshold.
 *   score_lt     — value: "objective:threshold". Visible if score < threshold.
 *   score_eq     — value: "objective:value".     Visible if score == value.
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
        SET_VAR, ADD_VAR, SUB_VAR, RESET_VAR, CLEAR_VARS;

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
        VAR_EQ, VAR_GT, VAR_LT, VAR_SET;

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
            List<ButtonAction> actionsOff
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
            Optional<ToggleDefinition> toggle
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
                    clickType, condition, List.of(), Optional.of(toggle));
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

        return new Button(slot, page, item, name, lore, glint, clickType, condition, actions, Optional.empty());
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

        return new ToggleDefinition(tag, itemOn, itemOff, nameOn, nameOff,
                loreOn, loreOff, glintOn, glintOff, actionsOn, actionsOff);
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
