package dev.toolkitmc.guiapi;

import dev.toolkitmc.guiapi.command.GuiCommand;
import dev.toolkitmc.guiapi.config.GuiApiConfig;
import dev.toolkitmc.guiapi.loader.GuiRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceLoader;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuiApiMod implements ModInitializer {

    public static final String MOD_ID = "guiapi";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[GuiAPI] Initializing...");

        GuiApiConfig.INSTANCE.load();

        // 1.21.9 Resource Loader API v1:
        // ResourceManagerHelper.get(...).registerReloadListener(listener)
        // is replaced by:
        // ResourceLoader.get(...).registerReloader(identifier, reloader)
        ResourceLoader.get(ResourceType.SERVER_DATA)
                .registerReloader(
                        Identifier.of(MOD_ID, "gui_registry"),
                        GuiRegistry.INSTANCE
                );

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                GuiCommand.register(dispatcher));

        LOGGER.info("[GuiAPI] Ready.");
    }
}
