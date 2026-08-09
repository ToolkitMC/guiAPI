package dev.toolkitmc.guiapi.component;

import dev.toolkitmc.guiapi.GuiApiMod;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/**
 * Registers guiAPI's custom item data components.
 * Call {@link #register()} once from {@code GuiApiMod#onInitialize()}.
 */
public final class GuiApiComponents {

    private GuiApiComponents() {}

    public static final DataComponentType<OpenGuiComponent> OPEN_GUI = Registry.register(
            BuiltInRegistries.DATA_COMPONENT_TYPE,
            Identifier.fromNamespaceAndPath(GuiApiMod.MOD_ID, "open_gui"),
            DataComponentType.<OpenGuiComponent>builder()
                    .persistent(OpenGuiComponent.CODEC)
                    .build()
    );

    /** No-op body — referencing this class from the mod initializer is enough to trigger the static init above. */
    public static void register() {}
}
