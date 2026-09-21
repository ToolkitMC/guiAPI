package dev.toolkitmc.guiapi.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Lossless GuiDefinition → JSON. Mirrors every field {@link GuiDefinition#parse}
 * reads, so parse(serialize(def)) reproduces the definition. The previous
 * serializer in GuiRegistry silently dropped cooldown, else_item, macros,
 * widgets, open gate, on_open/on_close, custom model data, action delay/condition
 * and composite conditions whenever the in-game editor saved a GUI.
 */
public final class GuiSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private GuiSerializer() {}

    public static String toJsonString(GuiDefinition def) {
        return GSON.toJson(toJson(def));
    }

    public static JsonObject toJson(GuiDefinition def) {
        JsonObject o = new JsonObject();
        o.addProperty("title", def.getTitle());
        o.addProperty("rows", def.getRows());
        o.addProperty("container_type", def.getContainerType().name().toLowerCase());
        o.addProperty("tick_rate", def.getTickRate());
        o.addProperty("close_on_move", def.isCloseOnMove());

        def.getFiller().ifPresent(f -> {
            JsonObject fo = new JsonObject();
            fo.addProperty("item", f.item());
            fo.addProperty("name", f.name());
            fo.addProperty("glint", f.glint());
            fo.addProperty("hide_tooltip", f.hideTooltip());
            o.add("filler", fo);
        });

        def.getOpenCondition().ifPresent(c -> o.add("open_condition", condition(c)));
        if (!def.getOpenCost().isEmpty()) o.addProperty("open_cost", def.getOpenCost());
        putActions(o, "on_deny", def.getOnDeny());
        putActions(o, "on_open", def.getOnOpen());
        putActions(o, "on_close", def.getOnClose());

        if (!def.getMacros().isEmpty()) {
            JsonObject mo = new JsonObject();
            for (Map.Entry<String, List<GuiDefinition.ButtonAction>> e : def.getMacros().entrySet()) {
                JsonArray arr = new JsonArray();
                for (GuiDefinition.ButtonAction a : e.getValue()) arr.add(action(a));
                mo.add(e.getKey(), arr);
            }
            o.add("macros", mo);
        }

        JsonArray buttons = new JsonArray();
        for (GuiDefinition.Button b : def.getButtons()) buttons.add(button(b));
        o.add("buttons", buttons);

        if (!def.getProgressBars().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (GuiDefinition.ProgressBarWidget p : def.getProgressBars()) {
                JsonObject po = new JsonObject();
                po.addProperty("start_slot", p.startSlot());
                po.addProperty("length", p.length());
                po.addProperty("page", p.page());
                po.addProperty("value_source", p.valueSource());
                po.addProperty("max_value", p.maxValue());
                po.addProperty("filled_item", p.filledItem());
                po.addProperty("empty_item", p.emptyItem());
                po.addProperty("name", p.name());
                putStrings(po, "lore", p.lore());
                arr.add(po);
            }
            o.add("progress_bars", arr);
        }

        if (!def.getDisplays().isEmpty()) {
            JsonArray arr = new JsonArray();
            for (GuiDefinition.StaticDisplayWidget d : def.getDisplays()) {
                JsonObject dobj = new JsonObject();
                dobj.addProperty("slot", d.slot());
                dobj.addProperty("page", d.page());
                dobj.addProperty("item", d.item());
                dobj.addProperty("name", d.name());
                putStrings(dobj, "lore", d.lore());
                dobj.addProperty("glint", d.glint());
                dobj.addProperty("amount", d.amount());
                d.condition().ifPresent(c -> dobj.add("condition", condition(c)));
                arr.add(dobj);
            }
            o.add("displays", arr);
        }
        return o;
    }

    // ── Buttons ──────────────────────────────────────────────────────────────

    private static JsonObject button(GuiDefinition.Button b) {
        JsonObject o = new JsonObject();
        o.addProperty("slot", b.slot());
        o.addProperty("page", b.page());
        o.addProperty("click_type", b.clickType().name().toLowerCase());
        if (b.cooldown() > 0) o.addProperty("cooldown", b.cooldown());
        b.condition().ifPresent(c -> o.add("condition", condition(c)));
        b.elseDisplay().ifPresent(e -> o.add("else_item", elseDisplay(e)));

        if (b.toggle().isPresent()) {
            o.add("toggle", toggle(b.toggle().get()));
            return o;
        }
        o.addProperty("item", b.item());
        o.addProperty("name", b.name());
        putStrings(o, "lore", b.lore());
        o.addProperty("glint", b.glint());
        o.addProperty("amount", b.amount());
        o.addProperty("hide_tooltip", b.hideTooltip());
        o.addProperty("hide_additional_tooltip", b.hideAdditionalTooltip());
        putCmd(o, "custom_model_data", b.customModelData());
        b.itemModel().ifPresent(m -> o.addProperty("item_model", m));
        JsonArray actions = new JsonArray();
        for (GuiDefinition.ButtonAction a : b.actions()) actions.add(action(a));
        o.add("actions", actions);
        return o;
    }

    private static JsonObject elseDisplay(GuiDefinition.ConditionalDisplay e) {
        JsonObject o = new JsonObject();
        o.addProperty("item", e.item());
        o.addProperty("name", e.name());
        putStrings(o, "lore", e.lore());
        o.addProperty("glint", e.glint());
        o.addProperty("amount", e.amount());
        o.addProperty("hide_tooltip", e.hideTooltip());
        o.addProperty("hide_additional_tooltip", e.hideAdditionalTooltip());
        putCmd(o, "custom_model_data", e.customModelData());
        e.itemModel().ifPresent(m -> o.addProperty("item_model", m));
        return o;
    }

    private static JsonObject toggle(GuiDefinition.ToggleDefinition t) {
        JsonObject o = new JsonObject();
        o.addProperty("tag", t.tag());
        o.addProperty("item_on", t.itemOn());
        o.addProperty("item_off", t.itemOff());
        o.addProperty("name_on", t.nameOn());
        o.addProperty("name_off", t.nameOff());
        putStrings(o, "lore_on", t.loreOn());
        putStrings(o, "lore_off", t.loreOff());
        o.addProperty("glint_on", t.glintOn());
        o.addProperty("glint_off", t.glintOff());
        o.addProperty("amount_on", t.amountOn());
        o.addProperty("amount_off", t.amountOff());
        o.addProperty("hide_tooltip_on", t.hideTooltipOn());
        o.addProperty("hide_tooltip_off", t.hideTooltipOff());
        o.addProperty("hide_additional_tooltip_on", t.hideAdditionalTooltipOn());
        o.addProperty("hide_additional_tooltip_off", t.hideAdditionalTooltipOff());
        putCmd(o, "custom_model_data_on", t.customModelDataOn());
        putCmd(o, "custom_model_data_off", t.customModelDataOff());
        t.itemModelOn().ifPresent(m -> o.addProperty("item_model_on", m));
        t.itemModelOff().ifPresent(m -> o.addProperty("item_model_off", m));
        JsonArray on = new JsonArray();
        for (GuiDefinition.ButtonAction a : t.actionsOn()) on.add(action(a));
        o.add("actions_on", on);
        JsonArray off = new JsonArray();
        for (GuiDefinition.ButtonAction a : t.actionsOff()) off.add(action(a));
        o.add("actions_off", off);
        return o;
    }

    // ── Shared pieces ────────────────────────────────────────────────────────

    static JsonObject action(GuiDefinition.ButtonAction a) {
        JsonObject o = new JsonObject();
        o.addProperty("type", a.type().name().toLowerCase());
        o.addProperty("value", a.value());
        if (!a.var().isEmpty()) o.addProperty("var", a.var());
        if (a.runWith() == GuiDefinition.RunWith.CONSOLE) o.addProperty("run_with", "console");
        if (a.delay() > 0) o.addProperty("delay", a.delay());
        a.condition().ifPresent(c -> o.add("condition", condition(c)));
        return o;
    }

    static JsonObject condition(GuiDefinition.ButtonCondition c) {
        JsonObject o = new JsonObject();
        o.addProperty("type", c.type().name().toLowerCase());
        switch (c.type()) {
            case NOT -> {
                if (!c.children().isEmpty()) o.add("condition", condition(c.children().get(0)));
            }
            case ALL, ANY -> {
                JsonArray arr = new JsonArray();
                for (GuiDefinition.ButtonCondition ch : c.children()) arr.add(condition(ch));
                o.add("conditions", arr);
            }
            default -> o.addProperty("value", c.value());
        }
        return o;
    }

    private static void putActions(JsonObject o, String key, List<GuiDefinition.ButtonAction> actions) {
        if (actions.isEmpty()) return;
        JsonArray arr = new JsonArray();
        for (GuiDefinition.ButtonAction a : actions) arr.add(action(a));
        o.add(key, arr);
    }

    private static void putStrings(JsonObject o, String key, List<String> list) {
        if (list.isEmpty()) return;
        JsonArray arr = new JsonArray();
        for (String s : list) arr.add(s);
        o.add(key, arr);
    }

    private static void putCmd(JsonObject o, String key, Optional<GuiDefinition.CustomModelDataConfig> cmd) {
        if (cmd.isEmpty()) return;
        GuiDefinition.CustomModelDataConfig c = cmd.get();
        JsonObject co = new JsonObject();
        JsonArray floats = new JsonArray();   c.floats().forEach(floats::add);
        JsonArray flags = new JsonArray();    c.flags().forEach(flags::add);
        JsonArray strings = new JsonArray();  c.strings().forEach(strings::add);
        JsonArray colors = new JsonArray();   c.colors().forEach(colors::add);
        co.add("floats", floats);
        co.add("flags", flags);
        co.add("strings", strings);
        co.add("colors", colors);
        o.add(key, co);
    }
}
