package dev.toolkitmc.guiapi.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.toolkitmc.guiapi.GuiApiMod;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

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
public class GuiRegistry extends SinglePreparationResourceReloader<Map<Identifier, GuiDefinition>>
        implements IdentifiableResourceReloadListener {

    public static final GuiRegistry INSTANCE = new GuiRegistry();

    private static final String DIRECTORY = "gui";
    private static final Gson GSON = new Gson();

    private final Map<Identifier, GuiDefinition> definitions = new HashMap<>();

    /** Addon-registered GUIs — survive datapack reloads. */
    private final Map<Identifier, GuiDefinition> addonDefinitions = new HashMap<>();

    private GuiRegistry() {}

    @Override
    public Identifier getFabricId() {
        return Identifier.of("guiapi", "gui_registry");
    }

    // ── ResourceReloader impl ────────────────────────────────────────────────

    @Override
    protected Map<Identifier, GuiDefinition> prepare(ResourceManager manager, Profiler profiler) {
        Map<Identifier, GuiDefinition> loaded = new HashMap<>();

        // findAllResources finds files matching data/<ns>/gui/<path>.json
        manager.findResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
               .forEach((fileId, resource) -> {
                   try (InputStreamReader reader = new InputStreamReader(
                           resource.getInputStream(), StandardCharsets.UTF_8)) {

                       JsonObject json = GSON.fromJson(reader, JsonObject.class);

                       // Reject unsupported GUI types explicitly (e.g. legacy "dialog" format)
                       if (json.has("type") && !json.get("type").getAsString().equals("barrel")) {
                           GuiApiMod.LOGGER.warn("[GuiAPI] Skipping {} — unsupported type '{}'. Only chest/barrel GUIs are supported.",
                                   fileId, json.get("type").getAsString());
                           return;
                       }

                       // fileId looks like: <ns>:gui/<name>.json
                       // Compute logical GUI id: <ns>:<name>  (strip "gui/" prefix + ".json")
                       String path = fileId.getPath(); // "gui/my_gui.json"
                       String stripped = path.substring(DIRECTORY.length() + 1, path.length() - 5);
                       Identifier guiId = Identifier.of(fileId.getNamespace(), stripped);

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
    protected void apply(Map<Identifier, GuiDefinition> prepared, ResourceManager manager, Profiler profiler) {
        definitions.clear();
        definitions.putAll(prepared);
        // Addon GUIs are re-applied after every reload so they are never wiped.
        // Datapack entries take priority if the same ID exists in both.
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

    /**
     * Addon API — register a GUI definition from Java code (e.g. another Fabric mod).
     * Addon registrations survive a datapack reload; they are re-applied after every
     * {@link #apply} call so they are never wiped by {@code /reload} or {@code /guiapi reload}.
     *
     * <p>Call this from your mod's {@code onInitialize()} or from a
     * {@link net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents#SERVER_STARTED}
     * callback.
     *
     * @param definition The GUI to register.
     * @throws IllegalArgumentException if a GUI with the same ID is already registered
     *                                  by another addon (datapack GUIs are always overrideable).
     */
    public void registerAddon(GuiDefinition definition) {
        Identifier id = definition.getId();
        if (addonDefinitions.containsKey(id)) {
            throw new IllegalArgumentException("[GuiAPI] Addon GUI already registered: " + id);
        }
        addonDefinitions.put(id, definition);
        definitions.put(id, definition);
        GuiApiMod.LOGGER.info("[GuiAPI] Addon registered GUI: {}", id);
    }

    /**
     * Addon API — unregister a previously registered addon GUI.
     * No-op if the ID was never registered as an addon.
     */
    public void unregisterAddon(Identifier id) {
        if (addonDefinitions.remove(id) != null) {
            definitions.remove(id);
            GuiApiMod.LOGGER.info("[GuiAPI] Addon unregistered GUI: {}", id);
        }
    }
}
