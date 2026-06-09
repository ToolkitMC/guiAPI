package dev.toolkitmc.guiapi.gui;

import java.util.ArrayList;
import java.util.List;

public class GuiActionParser {

    public static GuiDefinition.ButtonAction parseActionFromString(String str) {
        str = str.trim();
        // Handle bare type names that require no value (e.g. "none", "clear_vars")
        GuiDefinition.ActionType bareType = tryParseBareType(str);
        if (bareType != null) {
            return new GuiDefinition.ButtonAction(bareType, "", GuiDefinition.RunWith.PLAYER, "", 0);
        }

        String[] parts = str.split(":", 3);
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

    /**
     * Returns the ActionType if the entire string is a no-value action keyword,
     * otherwise returns null.
     */
    private static GuiDefinition.ActionType tryParseBareType(String s) {
        return switch (s.toLowerCase()) {
            case "none"       -> GuiDefinition.ActionType.NONE;
            case "clear_vars" -> GuiDefinition.ActionType.CLEAR_VARS;
            case "clear_effects" -> GuiDefinition.ActionType.CLEAR_EFFECTS;
            case "refresh"    -> GuiDefinition.ActionType.REFRESH;
            default           -> null;
        };
    }

    public static String serializeActionsToString(List<GuiDefinition.ButtonAction> actions) {
        List<String> list = new ArrayList<>();
        for (GuiDefinition.ButtonAction act : actions) {
            String prefix = act.type().name().toLowerCase();
            if (act.type() == GuiDefinition.ActionType.SET_VAR ||
                act.type() == GuiDefinition.ActionType.ADD_VAR ||
                act.type() == GuiDefinition.ActionType.SUB_VAR) {
                list.add(prefix + ":" + act.var() + ":" + act.value());
            } else if (act.type() == GuiDefinition.ActionType.NONE ||
                       act.type() == GuiDefinition.ActionType.CLEAR_VARS ||
                       act.type() == GuiDefinition.ActionType.CLEAR_EFFECTS ||
                       act.type() == GuiDefinition.ActionType.REFRESH) {
                // No-value actions: serialize without trailing colon
                list.add(prefix);
            } else {
                list.add(prefix + ":" + act.value());
            }
        }
        return String.join(";", list);
    }
}
