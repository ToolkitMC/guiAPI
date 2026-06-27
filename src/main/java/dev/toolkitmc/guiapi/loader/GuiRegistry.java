package dev.toolkitmc.guiapi.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import dev.toolkitmc.guiapi.GuiApiMod;
import dev.toolkitmc.guiapi.gui.GuiDefinition;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.server.MinecraftServer;
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
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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

        manager.findResources(DIRECTORY, id -> id.getPath().endsWith(".json"))
               .forEach((fileId, resource) -> {
                   try (InputStreamReader reader = new InputStreamReader(
                           resource.getInputStream(), StandardCharsets.UTF_8)) {

                       JsonObject json = GSON.fromJson(reader, JsonObject.class);

                       if (json.has("type") && !json.get("type").getAsString().equals("barrel")) {
                           GuiApiMod.LOGGER.warn("[GuiAPI] Skipping {} — unsupported type '{}'. Only chest/barrel GUIs are supported.",
                                   fileId, json.get("type").getAsString());
                           return;
                       }

                       String path = fileId.getPath();
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
            java.nio.file.Path datapacksPath = server.getSavePath(net.minecraft.util.WorldSavePath.DATAPACKS);
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
        JsonObject obj = new JsonObject();
        obj.addProperty("title", def.getTitle());
        obj.addProperty("rows", def.getRows());
        obj.addProperty("tick_rate", def.getTickRate());
        obj.addProperty("close_on_move", def.isCloseOnMove());

        if (def.getFiller().isPresent()) {
            GuiDefinition.FillerConfig fill = def.getFiller().get();
            JsonObject fObj = new JsonObject();
            fObj.addProperty("item", fill.item());
            fObj.addProperty("name", fill.name());
            fObj.addProperty("glint", fill.glint());
            fObj.addProperty("hide_tooltip", fill.hideTooltip());
            obj.add("filler", fObj);
        }

        com.google.gson.JsonArray btnsArray = new com.google.gson.JsonArray();
        for (GuiDefinition.Button b : def.getButtons()) {
            JsonObject bObj = new JsonObject();
            bObj.addProperty("slot", b.slot());
            bObj.addProperty("page", b.page());
            bObj.addProperty("item", b.item());
            bObj.addProperty("name", b.name());
            bObj.addProperty("amount", b.amount());
            bObj.addProperty("glint", b.glint());
            bObj.addProperty("click_type", b.clickType().name().toLowerCase());
            bObj.addProperty("hide_tooltip", b.hideTooltip());
            bObj.addProperty("hide_additional_tooltip", b.hideAdditionalTooltip());

            // Serialize Lore lines (Fixed: no longer omitted during save!)
            com.google.gson.JsonArray loreArr = new com.google.gson.JsonArray();
            for (String l : b.lore()) {
                loreArr.add(l);
            }
            bObj.add("lore", loreArr);

            if (b.condition().isPresent()) {
                GuiDefinition.ButtonCondition cond = b.condition().get();
                JsonObject cObj = new JsonObject();
                cObj.addProperty("type", cond.type().name().toLowerCase());
                cObj.addProperty("value", cond.value());
                bObj.add("condition", cObj);
            }

            if (b.toggle().isPresent()) {
                GuiDefinition.ToggleDefinition tgl = b.toggle().get();
                JsonObject tObj = new JsonObject();
                tObj.addProperty("tag", tgl.tag());
                tObj.addProperty("item_on", tgl.itemOn());
                tObj.addProperty("item_off", tgl.itemOff());
                tObj.addProperty("name_on", tgl.nameOn());
                tObj.addProperty("name_off", tgl.nameOff());
                tObj.addProperty("glint_on", tgl.glintOn());
                tObj.addProperty("glint_off", tgl.glintOff());
                tObj.addProperty("amount_on", tgl.amountOn());
                tObj.addProperty("amount_off", tgl.amountOff());
                tObj.addProperty("hide_tooltip_on", tgl.hideTooltipOn());
                tObj.addProperty("hide_tooltip_off", tgl.hideTooltipOff());
                tObj.addProperty("hide_additional_tooltip_on", tgl.hideAdditionalTooltipOn());
                tObj.addProperty("hide_additional_tooltip_off", tgl.hideAdditionalTooltipOff());

                com.google.gson.JsonArray actionsOnArr = new com.google.gson.JsonArray();
                for (GuiDefinition.ButtonAction act : tgl.actionsOn()) {
                    actionsOnArr.add(serializeAction(act));
                }
                tObj.add("actions_on", actionsOnArr);

                com.google.gson.JsonArray actionsOffArr = new com.google.gson.JsonArray();
                for (GuiDefinition.ButtonAction act : tgl.actionsOff()) {
                    actionsOffArr.add(serializeAction(act));
                }
                tObj.add("actions_off", actionsOffArr);

                bObj.add("toggle", tObj);
            } else {
                com.google.gson.JsonArray actionsArr = new com.google.gson.JsonArray();
                for (GuiDefinition.ButtonAction act : b.actions()) {
                    actionsArr.add(serializeAction(act));
                }
                bObj.add("actions", actionsArr);
            }

            btnsArray.add(bObj);
        }
        obj.add("buttons", btnsArray);

        return GSON.toJson(obj);
    }

    private static JsonObject serializeAction(GuiDefinition.ButtonAction act) {
        JsonObject aObj = new JsonObject();
        aObj.addProperty("type", act.type().name().toLowerCase());
        if (!act.value().isEmpty()) aObj.addProperty("value", act.value());
        if (act.runWith() != GuiDefinition.RunWith.PLAYER) aObj.addProperty("run_with", act.runWith().name().toLowerCase());
        if (!act.var().isEmpty()) aObj.addProperty("var", act.var());
        if (act.delay() > 0) aObj.addProperty("delay", act.delay());
        if (!act.actions().isEmpty()) {
            com.google.gson.JsonArray nested = new com.google.gson.JsonArray();
            for (GuiDefinition.ButtonAction nestedAction : act.actions()) {
                nested.add(serializeAction(nestedAction));
            }
            aObj.add("actions", nested);
        }
        return aObj;
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
