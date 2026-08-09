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
                "\n" +
                "Anvil input: anvil_input action saves text to a variable and {input}\n" +
                "  Example: {\"type\": \"anvil_input\", \"var\": \"myVar\", \"value\": \"Enter name|Default\"}\n" +
                "\n" +
                "Button JSON fields:\n" +
                "  slot, page, item, name, lore, glint\n" +
                "  click_type: any | left | right | shift\n" +
                "  condition:  has_tag | not_tag | score_gt | score_lt | score_eq\n" +
                "              var_eq | var_gt | var_lt | var_set\n" +
                "  actions:    run_command | close | open_gui | message | sound\n" +
                "              next_page | prev_page | goto_page | run_function\n" +
                "              set_var | add_var | sub_var | reset_var | clear_vars\n" +
                "              anvil_input" ;
        ctx.getSource().sendSuccess(() -> Component.literal(help), false);
        return 1;
    }
}
