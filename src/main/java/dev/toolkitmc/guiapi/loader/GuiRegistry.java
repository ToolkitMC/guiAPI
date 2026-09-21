package dev.toolkitmc.guiapi.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.toolkitmc.guiapi.GuiApiMod;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads all data/<ns>/gui/*.json files from the datapack resource manager.
 *
 * Registered as a server-side resource reload listener so it fires on
 * /reload as well as world load.
 */
public class GuiRegistry extends SimplePreparableReloadListener<Map<Identifier, GuiDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final GuiRegistry INSTANCE = new GuiRegistry();

    private static final String DIRECTORY = "gui";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<Identifier, GuiDefinition> definitions = new HashMap<>();

    /** Addon-registered GUIs — survive datapack reloads. */
    private final Map<Identifier, GuiDefinition> addonDefinitions = new HashMap<>();

    private GuiRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.fromNamespaceAndPath("guiapi", "gui_registry");
    }

    // ── ResourceReloader impl ────────────────────────────────────────────────

    @Override
    protected Map<Identifier, GuiDefinition> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, GuiDefinition> loaded = new HashMap<>();

        manager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
               .forEach((fileId, resource) -> {
                   try (InputStreamReader reader = new InputStreamReader(
                           resource.open(), StandardCharsets.UTF_8)) {

                       JsonObject json = GSON.fromJson(reader, JsonObject.class);

                       if (json.has("type") && !json.get("type").getAsString().equals("barrel")) {
                           GuiApiMod.LOGGER.warn("[GuiAPI] Skipping {} — unsupported type '{}'. Only chest/barrel GUIs are supported.",
                                   fileId, json.get("type").getAsString());
                           return;
                       }

                       String path = fileId.getPath();
                       String stripped = path.substring(DIRECTORY.length() + 1, path.length() - 5);
                       Identifier guiId = Identifier.fromNamespaceAndPath(fileId.getNamespace(), stripped);

                       GuiDefinition def = GuiDefinition.parse(guiId, json);
                       loaded.put(guiId, def);

                       GuiApiMod.LOGGER.info("[GuiAPI] Loaded GUI: {}", guiId);
                   } catch (Exception e) {
                       GuiApiMod.LOGGER.error("[GuiAPI] Failed to load GUI {}: {}", fileId, e.getMessage());
                   }
               });

        return loaded;
    }

    @Override
    protected void apply(Map<Identifier, GuiDefinition> prepared, ResourceManager manager, ProfilerFiller profiler) {
        definitions.clear();
        definitions.putAll(prepared);
        addonDefinitions.forEach(definitions::putIfAbsent);
        GuiApiMod.LOGGER.info("[GuiAPI] Registered {} GUI definitions ({} from addons).",
                definitions.size(), addonDefinitions.size());
    }

    // ── Public API ───────────────────────────────────────────────────────────

    public Optional<GuiDefinition> get(Identifier id) {
        return Optional.ofNullable(definitions.get(id));
    }

    public Map<Identifier, GuiDefinition> getAll() {
        return Map.copyOf(definitions);
    }

    public void put(Identifier id, GuiDefinition def) {
        definitions.put(id, def);
    }

    /**
     * Serializes and saves a GuiDefinition directly back to its datapack JSON file on disk.
     */
    public boolean saveToDisk(MinecraftServer server, Identifier id, GuiDefinition def) {
        try {
            java.nio.file.Path datapacksPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.DATAPACK_DIR);
            if (!java.nio.file.Path.class.isInstance(datapacksPath) || !java.nio.file.Files.exists(datapacksPath)) return false;

            // Scan all loaded datapack subfolders
            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.list(datapacksPath)) {
                java.util.List<java.nio.file.Path> datapacks = stream.toList();
                for (java.nio.file.Path pack : datapacks) {
                    java.nio.file.Path targetFile = pack.resolve("data")
                            .resolve(id.getNamespace())
                            .resolve("gui")
                            .resolve(id.getPath() + ".json");

                    if (java.nio.file.Files.exists(targetFile)) {
                        // Serialize definition back to string format
                        String jsonString = serializeDefinition(def);
                        java.nio.file.Files.writeString(targetFile, jsonString, java.nio.charset.StandardCharsets.UTF_8);
                        GuiApiMod.LOGGER.info("[GuiAPI] Successfully persisted GUI {} to disk at: {}", id, targetFile);
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            GuiApiMod.LOGGER.error("[GuiAPI] Failed to save GUI {} to disk: {}", id, e.getMessage());
        }
        return false;
    }

    private static String serializeDefinition(GuiDefinition def) {
        return dev.toolkitmc.guiapi.gui.GuiSerializer.toJsonString(def);
    }

    /** Addon API — register a GUI definition from Java code */
    public void registerAddon(GuiDefinition definition) {
        Identifier id = definition.getId();
        if (addonDefinitions.containsKey(id)) {
            throw new IllegalArgumentException("[GuiAPI] Addon GUI already registered: " + id);
        }
        addonDefinitions.put(id, definition);
        definitions.put(id, definition);
        GuiApiMod.LOGGER.info("[GuiAPI] Addon registered GUI: {}", id);
    }

    /** Addon API — unregister a previously registered addon GUI. */
    public void unregisterAddon(Identifier id) {
        if (addonDefinitions.remove(id) != null) {
            definitions.remove(id);
            GuiApiMod.LOGGER.info("[GuiAPI] Addon unregistered GUI: {}", id);
        }
    }
}
