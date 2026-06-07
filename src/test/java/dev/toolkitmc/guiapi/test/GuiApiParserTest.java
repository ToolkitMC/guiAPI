package dev.toolkitmc.guiapi.test;

import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.gui.GuiActionParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GuiApiParserTest {

    @Test
    public void testParseActionFromString() {
        // Test standard close action
        GuiDefinition.ButtonAction action1 = GuiActionParser.parseActionFromString("close");
        assertNotNull(action1);
        assertEquals(GuiDefinition.ActionType.CLOSE, action1.type());

        // Test message action
        GuiDefinition.ButtonAction action2 = GuiActionParser.parseActionFromString("message:Hello World!");
        assertNotNull(action2);
        assertEquals(GuiDefinition.ActionType.MESSAGE, action2.type());
        assertEquals("Hello World!", action2.value());

        // Test set_var action
        GuiDefinition.ButtonAction action3 = GuiActionParser.parseActionFromString("set_var:count:42");
        assertNotNull(action3);
        assertEquals(GuiDefinition.ActionType.SET_VAR, action3.type());
        assertEquals("count", action3.var());
        assertEquals("42", action3.value());
    }

    @Test
    public void testSerializeActionsToString() {
        List<GuiDefinition.ButtonAction> actions = List.of(
                new GuiDefinition.ButtonAction(GuiDefinition.ActionType.MESSAGE, "Purchased successfully!"),
                new GuiDefinition.ButtonAction(GuiDefinition.ActionType.REFRESH, "")
        );

        String serialized = GuiActionParser.serializeActionsToString(actions);
        assertEquals("message:Purchased successfully!;refresh:", serialized);
    }
}
