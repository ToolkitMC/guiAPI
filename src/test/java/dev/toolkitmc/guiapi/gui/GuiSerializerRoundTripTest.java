package dev.toolkitmc.guiapi.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * parse → serialize → parse must be lossless. Needs the Minecraft classpath
 * (Identifier), so it only runs under Gradle/CI.
 */
class GuiSerializerRoundTripTest {

    private static final String SRC = """
        {
          "title": "T", "rows": 4, "tick_rate": 10, "close_on_move": true,
          "container_type": "ender_chest",
          "filler": {"item": "minecraft:black_stained_glass_pane", "name": " ", "glint": false, "hide_tooltip": true},
          "open_condition": {"type": "all", "conditions": [
              {"type": "has_tag", "value": "vip"},
              {"type": "not", "condition": {"type": "has_tag", "value": "banned"}}]},
          "on_deny": [{"type": "message", "value": "no"}],
          "on_open": [{"type": "sound", "value": "minecraft:block.chest.open", "delay": 5}],
          "on_close": [{"type": "run_command", "value": "say bye", "run_with": "console"}],
          "macros": {"m": [{"type": "message", "value": "hi"}]},
          "buttons": [
            {"slot": 1, "cooldown": 100, "click_type": "left", "item": "minecraft:diamond",
             "name": "A", "lore": ["l1"], "amount": "3",
             "custom_model_data": {"floats": [1.5], "flags": [true], "strings": ["s"], "colors": [7]},
             "item_model": "pack:m",
             "condition": {"type": "any", "conditions": [{"type": "level_gt", "value": "5"}]},
             "else_item": {"item": "minecraft:barrier", "name": "locked"},
             "actions": [{"type": "add_var", "var": "k", "value": "2", "delay": 3,
                          "condition": {"type": "has_tag", "value": "x"}}]},
            {"slot": 2, "toggle": {"tag": "t", "lore_on": ["on"], "custom_model_data_on": 9}}
          ],
          "progress_bars": [{"start_slot": 9, "length": 5, "max_value": 50, "value_source": "score:coins"}],
          "displays": [{"slot": 0, "item": "minecraft:paper", "name": "d", "amount": "2"}]
        }
        """;

    @Test
    void roundTripIsStable() {
        Identifier id = Identifier.fromNamespaceAndPath("t", "x");
        GuiDefinition first = GuiDefinition.parse(id, JsonParser.parseString(SRC).getAsJsonObject());

        JsonObject out1 = GuiSerializer.toJson(first);
        GuiDefinition second = GuiDefinition.parse(id, out1);
        JsonObject out2 = GuiSerializer.toJson(second);

        // Serializing the re-parsed definition must give the identical document.
        assertEquals(out1, out2);
    }

    @Test
    void preservesFieldsThatUsedToBeDropped() {
        Identifier id = Identifier.fromNamespaceAndPath("t", "x");
        GuiDefinition def = GuiDefinition.parse(id, JsonParser.parseString(SRC).getAsJsonObject());
        JsonObject out = GuiSerializer.toJson(def);

        assertEquals("ender_chest", out.get("container_type").getAsString());
        assertTrue(out.has("open_condition"));
        assertTrue(out.has("on_deny"));
        assertTrue(out.has("on_open"));
        assertTrue(out.has("on_close"));
        assertTrue(out.has("macros"));
        assertTrue(out.has("progress_bars"));
        assertTrue(out.has("displays"));

        JsonObject b = out.getAsJsonArray("buttons").get(0).getAsJsonObject();
        assertEquals(100, b.get("cooldown").getAsInt());
        assertTrue(b.has("else_item"));
        assertTrue(b.has("custom_model_data"));
        assertEquals("pack:m", b.get("item_model").getAsString());
        JsonObject act = b.getAsJsonArray("actions").get(0).getAsJsonObject();
        assertEquals(3, act.get("delay").getAsInt());
        assertTrue(act.has("condition"));
    }
}
