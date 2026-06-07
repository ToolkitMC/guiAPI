package dev.toolkitmc.guiapi.gui;

import java.util.ArrayList;
import java.util.List;

public class GuiActionParser {

    public static GuiDefinition.ButtonAction parseActionFromString(String str) {
        String[] parts = str.trim().split(":", 3);
        if (parts.length >= 2) {
            GuiDefinition.ActionType type = GuiDefinition.ActionType.fromString(parts[0]);
            String val = parts[1];
            String var = "";
            if (type == GuiDefinition.ActionType.SET_VAR ||
                type == GuiDefinition.ActionType.ADD_VAR ||
                type == GuiDefinition.ActionType.SUB_VAR ||
                type == GuiDefinition.ActionType.RESET_VAR) {
                var = parts[1];
                val = parts.length > 2 ? parts[2] : "";
            } else if (parts.length > 2) {
                // Reconstruct value that contains colons
                val = parts[1] + ":" + parts[2];
            }
            return new GuiDefinition.ButtonAction(type, val, GuiDefinition.RunWith.PLAYER, var, 0);
        }
        return new GuiDefinition.ButtonAction(GuiDefinition.ActionType.CLOSE, "");
    }

    public static String serializeActionsToString(List<GuiDefinition.ButtonAction> actions) {
        List<String> list = new ArrayList<>();
        for (GuiDefinition.ButtonAction act : actions) {
            String prefix = act.type().name().toLowerCase();
            if (act.type() == GuiDefinition.ActionType.SET_VAR ||
                act.type() == GuiDefinition.ActionType.ADD_VAR ||
                act.type() == GuiDefinition.ActionType.SUB_VAR) {
                list.add(prefix + ":" + act.var() + ":" + act.value());
            } else {
                list.add(prefix + ":" + act.value());
            }
        }
        return String.join(";", list);
    }
}
