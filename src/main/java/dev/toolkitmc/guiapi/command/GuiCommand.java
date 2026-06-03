package dev.toolkitmc.guiapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import dev.toolkitmc.guiapi.gui.GuiVarStore;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * /guiapi open <namespace:id> [<targets>]
 * /guiapi list
 * /guiapi reload
 * /guiapi help
 *
 * Permission level 2 required.
 */
public class GuiCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("guiapi")
                .requires(src -> src.hasPermissionLevel(
                        dev.toolkitmc.guiapi.config.GuiApiConfig.INSTANCE.getPermissionLevel()))
                .executes(GuiCommand::showHelp)

                .then(CommandManager.literal("open")
                    .then(CommandManager.argument("id", IdentifierArgumentType.identifier())
                        .suggests((ctx, builder) -> {
                            String input = builder.getRemainingLowerCase();
                            GuiRegistry.INSTANCE.getAll().keySet().stream()
                                    .map(Identifier::toString)
                                    .filter(s -> s.contains(input))
                                    .forEach(builder::suggest);
                            return builder.buildFuture();
                        })

                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendError(
                                    Text.literal("[GuiAPI] Must be a player, or specify <targets>."));
                                return 0;
                            }
                            return openGui(ctx, List.of(player));
                        })

                        .then(CommandManager.argument("targets", EntityArgumentType.players())
                            .executes(ctx -> openGui(ctx,
                                    EntityArgumentType.getPlayers(ctx, "targets"))))
                    )
                )

                .then(CommandManager.literal("list")
                    .executes(GuiCommand::listGuis))

                .then(CommandManager.literal("reload")
                    .executes(GuiCommand::reloadGuis))

                .then(CommandManager.literal("help")
                    .executes(GuiCommand::showHelp))

                .then(CommandManager.literal("var")
                    .then(CommandManager.literal("get")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("key", StringArgumentType.word())
                                .executes(GuiCommand::varGet))))
                    .then(CommandManager.literal("set")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                            .then(CommandManager.argument("key", StringArgumentType.word())
                                .then(CommandManager.argument("value", StringArgumentType.greedyString())
                                    .executes(GuiCommand::varSet)))))
                    .then(CommandManager.literal("clear")
                        .then(CommandManager.argument("target", EntityArgumentType.player())
                            .executes(GuiCommand::varClear)))
                )
        );
    }

    // ── Subcommand handlers ──────────────────────────────────────────────────

    private static int openGui(CommandContext<ServerCommandSource> ctx,
                               Collection<ServerPlayerEntity> targets) {
        Identifier id = IdentifierArgumentType.getIdentifier(ctx, "id");

        GuiDefinition def = GuiRegistry.INSTANCE.get(id).orElse(null);
        if (def == null) {
            ctx.getSource().sendError(Text.literal("[GuiAPI] GUI not found: " + id));
            return 0;
        }

        for (ServerPlayerEntity player : targets) {
            BarrelGuiHandler.open(player, def);
        }

        ctx.getSource().sendFeedback(
                () -> Text.literal("[GuiAPI] Opened '" + id + "' for " + targets.size() + " player(s)."),
                false);
        return targets.size();
    }

    private static int listGuis(CommandContext<ServerCommandSource> ctx) {
        var all = GuiRegistry.INSTANCE.getAll();
        if (all.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("[GuiAPI] No GUIs loaded."), false);
            return 0;
        }
        StringBuilder sb = new StringBuilder("[GuiAPI] Loaded GUIs (" + all.size() + "):\n");
        all.forEach((id, def) ->
                sb.append("  ").append(id)
                  .append(" [rows=").append(def.getRows())
                  .append(", pages=").append(def.getPageCount()).append("]\n"));
        ctx.getSource().sendFeedback(() -> Text.literal(sb.toString().trim()), false);
        return all.size();
    }

    private static int reloadGuis(CommandContext<ServerCommandSource> ctx) {
        // Delegates to the server's full resource reload so GuiRegistry.apply()
        // fires through the normal Fabric reload pipeline — same as /reload.
        ctx.getSource().getServer()
                .reloadResources(ctx.getSource().getServer().getDataPackManager().getEnabledIds())
                .thenRun(() -> ctx.getSource().sendFeedback(
                        () -> Text.literal("[GuiAPI] Reload complete. " +
                                GuiRegistry.INSTANCE.getAll().size() + " GUI(s) loaded."),
                        true))
                .exceptionally(ex -> {
                    ctx.getSource().sendError(
                            Text.literal("[GuiAPI] Reload failed: " + ex.getMessage()));
                    return null;
                });
        return 1;
    }

    private static int varGet(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        String key = StringArgumentType.getString(ctx, "key");
        String val = GuiVarStore.INSTANCE.get(target.getUuid(), key);
        if (val == null) {
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[GuiAPI] " + target.getName().getString() + "." + key + " is not set."), false);
        } else {
            ctx.getSource().sendFeedback(
                    () -> Text.literal("[GuiAPI] " + target.getName().getString() + "." + key + " = " + val), false);
        }
        return val != null ? 1 : 0;
    }

    private static int varSet(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        String key   = StringArgumentType.getString(ctx, "key");
        String value = StringArgumentType.getString(ctx, "value");
        GuiVarStore.INSTANCE.set(target.getUuid(), key, value);
        ctx.getSource().sendFeedback(
                () -> Text.literal("[GuiAPI] Set " + target.getName().getString() + "." + key + " = " + value), false);
        return 1;
    }

    private static int varClear(CommandContext<ServerCommandSource> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity target = EntityArgumentType.getPlayer(ctx, "target");
        Map<String, String> vars = GuiVarStore.INSTANCE.getAll(target.getUuid());
        int count = vars.size();
        GuiVarStore.INSTANCE.clear(target.getUuid());
        ctx.getSource().sendFeedback(
                () -> Text.literal("[GuiAPI] Cleared " + count + " var(s) for " + target.getName().getString() + "."), false);
        return count;
    }

    private static int showHelp(CommandContext<ServerCommandSource> ctx) {
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
                "\n" +
                "Button JSON fields:\n" +
                "  slot, page, item, name, lore, glint\n" +
                "  click_type: any | left | right | shift\n" +
                "  condition:  has_tag | not_tag | score_gt | score_lt | score_eq\n" +
                "              var_eq | var_gt | var_lt | var_set\n" +
                "  actions:    run_command | close | open_gui | message | sound\n" +
                "              next_page | prev_page | goto_page\n" +
                "              set_var | add_var | sub_var | reset_var | clear_vars";
        ctx.getSource().sendFeedback(() -> Text.literal(help), false);
        return 1;
    }
}
