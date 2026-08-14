package dev.toolkitmc.guiapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.gui.GuiVarStore;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * /guiapi open <namespace:id> [<targets>]
 * /guiapi list
 * /guiapi reload
 * /guiapi help
 *
 * Permission level 2 (GAMEMASTERS) required.
 */
public class GuiCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("guiapi")
                .requires(src -> src.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(
                        dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getPermissionLevel()))))
                .executes(GuiCommand::showHelp)

                .then(Commands.literal("open")
                    .then(Commands.argument("id", IdentifierArgument.id())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemainingLowerCase();
                            GuiRegistry.INSTANCE.getAll().keySet().stream()
                                    .map(Identifier::toString)
                                    .filter(s -> s.contains(input))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })

                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendFailure(
                                    Component.literal("[GuiAPI] Must be a player, or specify <targets>."));
                                return 0;
                            }
                            return openGui(ctx, List.of(player));
                        })

                        .then(Commands.argument("targets", EntityArgument.players())
                            .executes(ctx -> openGui(ctx,
                                    EntityArgument.getPlayers(ctx, "targets"))))
                    )
                )

                .then(Commands.literal("list")
                    .executes(GuiCommand::listGuis))

                .then(Commands.literal("reload")
                    .executes(GuiCommand::reloadGuis))

                .then(Commands.literal("help")
                    .executes(GuiCommand::showHelp))

                .then(Commands.literal("var")
                    .then(Commands.literal("get")
                        .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("key", StringArgumentType.word())
                                .executes(GuiCommand::varGet))))
                    .then(Commands.literal("set")
                        .then(Commands.argument("target", EntityArgument.player())
                            .then(Commands.argument("key", StringArgumentType.word())
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                    .executes(GuiCommand::varSet)))))
                    .then(Commands.literal("clear")
                        .then(Commands.argument("target", EntityArgument.player())
                            .executes(GuiCommand::varClear)))
                )
        );
    }

    // ── Subcommand handlers ──────────────────────────────────────────────────

    private static int openGui(CommandContext<CommandSourceStack> ctx,
                               Collection<ServerPlayer> targets) {
        Identifier id = IdentifierArgument.getId(ctx, "id");

        GuiDefinition def = GuiRegistry.INSTANCE.get(id).orElse(null);
        if (def == null) {
            ctx.getSource().sendFailure(Component.literal("[GuiAPI] GUI not found: " + id));
            return 0;
        }

        for (ServerPlayer player : targets) {
            BarrelGuiHandler.open(player, def);
        }

        ctx.getSource().sendSuccess(
                () -> Component.literal("[GuiAPI] Opened '" + id + "' for " + targets.size() + " player(s)."),
                false);
        return targets.size();
    }

    private static int listGuis(CommandContext<CommandSourceStack> ctx) {
        var all = GuiRegistry.INSTANCE.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("[GuiAPI] No GUIs loaded."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[GuiAPI] Loaded GUIs (" + all.size() + "):\n");
        all.forEach((id, def) ->
                sb.append("  ").append(id)
                  .append(" [rows=").append(def.getRows())
                  .append(", pages=").append(def.getPageCount()).append("]\n"));
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString().trim()), false);
        return all.size();
    }

    private static int reloadGuis(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().getServer()
                .reloadResources(ctx.getSource().getServer().getPackRepository().getSelectedIds())
                .thenRun(() -> ctx.getSource().sendSuccess(
                        () -> Component.literal("[GuiAPI] Reload complete. " +
                                GuiRegistry.INSTANCE.getAll().size() + " GUI(s) loaded."),
                        true))
                .exceptionally(ex -> {
                    ctx.getSource().sendFailure(
                            Component.literal("[GuiAPI] Reload failed: " + ex.getMessage()));
                    return null;
                });
        return 1;
    }

    private static int varGet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String key = StringArgumentType.getString(ctx, "key");
        String val = GuiVarStore.INSTANCE.get(target.getUUID(), key);
        if (val == null) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[GuiAPI] " + target.getName().getString() + "." + key + " is not set."), false);
        } else {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[GuiAPI] " + target.getName().getString() + "." + key + " = " + val), false);
        }
        return val != null ? 1 : 0;
    }

    private static int varSet(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        String key   = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        GuiVarStore.INSTANCE.set(target.getUUID(), key, value);
        ctx.getSource().sendSuccess(
                () -> Component.literal("[GuiAPI] Set " + target.getName().getString() + "." + key + " = " + value), false);
        return 1;
    }

    private static int varClear(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
        Map<String, String> vars = GuiVarStore.INSTANCE.getAll(target.getUUID());
        int count = vars.size();
        GuiVarStore.INSTANCE.clear(target.getUUID());
        ctx.getSource().sendSuccess(
                () -> Component.literal("[GuiAPI] Cleared " + count + " var(s) for " + target.getName().getString() + "."), false);
        return count;
    }

    private static int showHelp(CommandContext<CommandSourceStack> ctx) {
        String help =
                "[GuiAPI] Commands (permission level 2):\n" +
                "  /guiapi open <id> [targets] - Open a GUI for yourself or target players\n" +
                "  /guiapi list               - List all loaded GUI definitions\n" +
                "  /guiapi reload             - Reload all datapack resources (including GUIs)\n" +
                "  /guiapi var get <player> <key>        - Get a runtime variable\n" +
                "  /guiapi var set <player> <key> <val>  - Set a runtime variable\n" +
                "  /guiapi var clear <player>            - Clear all runtime variables\n" +
                "  /guiapi help               - Show this help message\n" +
                "\n" +
                "Variable actions:  set_var | add_var | sub_var | reset_var | clear_vars\n" +
                "Variable conditions: var_eq | var_gt | var_lt | var_set\n" +
                "Variable placeholder: {var:key}\n" +
                "Input placeholder: {input}   (last anvil input)\n" +
                "XP placeholder:    {xp}      (player experience level)\n" +
                "\n" +
                "Macro functions: define reusable action blocks in JSON with \"macros\": {}\n" +
                "  actions: run_function:<macro_name>\n" +
                "  random:  run_random_function:<name>[*weight](,<name>[*weight]...)\n" +
                "           e.g. run_random_function:common*70,rare*25,legendary*5\n" +
                "\n" +
                "Anvil input: anvil_input action saves text to a variable and {input}\n" +
                "  Example: {\"type\": \"anvil_input\", \"var\": \"myVar\", \"value\": \"Enter name|Default\"}\n" +
                "\n" +
                "Item/XP actions:\n" +
                "  give_item:<itemId>:<amount>   - give item(s), overflow drops at feet\n" +
                "  take_item:<itemId>:<amount>   - remove item(s) from inventory\n" +
                "  add_xp:<n>                    - add n XP points\n" +
                "  add_xp:L<n>                   - add n XP levels (prefix with L)\n" +
                "\n" +
                "Button JSON fields:\n" +
                "  slot, page, item, name, lore, glint\n" +
                "  click_type: any | left | right | shift\n" +
                "  condition:  has_tag | not_tag | score_gt | score_lt | score_eq\n" +
                "              var_eq | var_gt | var_lt | var_set\n" +
                "              has_item | not_item | level_gt | level_lt\n" +
                "              health_gt | health_lt | food_gt | food_lt\n" +
                "              permission:<0-4>   (checks player's command permission level)\n" +
                "  actions:    run_command | close | open_gui | message | sound | action_bar\n" +
                "              next_page | prev_page | goto_page | run_function\n" +
                "              run_random_function | give_item | take_item | add_xp\n" +
                "              set_var | add_var | sub_var | reset_var | clear_vars\n" +
                "              set_score | add_score | sub_score\n" +
                "              add_effect | remove_effect | clear_effects\n" +
                "              anvil_input\n" +
                "\n" +
                "Conditional item display: add \"else_item\" (same fields as a button)\n" +
                "  alongside \"condition\" to show an alternate item instead of hiding\n" +
                "  the button when the condition is false. Button stays inert while\n" +
                "  showing else_item (clicks are ignored, not the normal actions).\n" +
                "\n" +
                "Non-button widgets (top-level GUI JSON fields):\n" +
                "  progress_bars: [{ start_slot, length, page, value_source,\n" +
                "                    max_value, filled_item, empty_item, name, lore }]\n" +
                "    value_source: \"score:<objective>\" or \"var:<key>\"\n" +
                "    Fills [start_slot, start_slot+length) proportionally, recalculated\n" +
                "    on every open/refresh (e.g. via tick_rate). Read-only, not clickable.\n" +
                "  displays: [{ slot, page, item, name, lore, glint, amount, condition }]\n" +
                "    A single read-only info item. Supports the same condition types as\n" +
                "    buttons. Clicks on a display slot are always ignored.";
        ctx.getSource().sendSuccess(() -> Component.literal(help), false);
        return 1;
    }
}
