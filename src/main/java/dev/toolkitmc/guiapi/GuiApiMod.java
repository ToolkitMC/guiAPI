package dev.toolkitmc.guiapi;

import dev.toolkitmc.guiapi.command.GuiCommand;
import dev.toolkitmc.guiapi.config.GuiApiConfig;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import dev.toolkitmc.guiapi.gui.BarrelGuiHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.packs.PackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuiApiMod implements ModInitializer {

    public static final String MOD_ID = "guiapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[GuiAPI] Initializing...");

        GuiApiConfig.INSTANCE.load();

        ResourceManagerHelper.get(PackType.SERVER_DATA)
                .registerReloadListener(GuiRegistry.INSTANCE);

        CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) ->
                GuiCommand.register(dispatcher));

        // Register Server Tick Event for Auto-Refreshing GUIs (tick_rate)
        ServerTickEvents.END_SERVER_TICK.register(BarrelGuiHandler::tick);

        LOGGER.info("[GuiAPI] Ready.");
    }
}
