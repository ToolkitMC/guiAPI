package dev.toolkitmc.guiapi.gui;

import java.util.function.Predicate;

/**
 * Boolean composition of conditions ({@code all}, {@code any}, {@code not}).
 * Kept free of Minecraft types so the logic is independent of how a single
 * (leaf) condition is evaluated.
 */
final class ConditionLogic {

    private ConditionLogic() {}

    /**
     * @param cond condition tree
     * @param leaf evaluates every non-composite condition
     *
     * Empty {@code all} is true, empty {@code any} is false, and a {@code not}
     * without a child is false (fails closed, so a broken gate never opens).
     */
    static boolean evaluate(GuiDefinition.ButtonCondition cond,
                            Predicate<GuiDefinition.ButtonCondition> leaf) {
        return switch (cond.type()) {
            case ALL -> {
                for (GuiDefinition.ButtonCondition child : cond.children()) {
                    if (!evaluate(child, leaf)) yield false;
                }
                yield true;
            }
            case ANY -> {
                for (GuiDefinition.ButtonCondition child : cond.children()) {
                    if (evaluate(child, leaf)) yield true;
                }
                yield false;
            }
            case NOT -> !cond.children().isEmpty() && !evaluate(cond.children().get(0), leaf);
            default -> leaf.test(cond);
        };
    }
}
