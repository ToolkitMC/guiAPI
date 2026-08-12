package dev.toolkitmc.guiapi.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Data carried by the {@code guiapi:open_gui} item component.
 *
 * Applied to an item via
 * {@code give @s <item>[guiapi:open_gui={gui:"namespace:gui_id",page:0}]}
 * to make right-clicking that item open the referenced GUI.
 *
 * Right-click only — see {@link dev.toolkitmc.guiapi.event.OpenGuiItemUseHandler}.
 * Left-click item activation has no reliable vanilla/Fabric server-side event
 * for "punch empty air", so it is intentionally not supported here.
 *
 * @param gui  Namespaced id of the target GUI, e.g. "example:vip_menu"
 * @param page Page to open on (0-indexed). Clamped against the GUI's actual
 *             page count at open time — an out-of-range value here does not
 *             crash, it just gets clamped by {@code BarrelGuiHandler.open}.
 */
public record OpenGuiComponent(String gui, int page) {

    public static final Codec<OpenGuiComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("gui").forGetter(OpenGuiComponent::gui),
            Codec.INT.optionalFieldOf("page", 0).forGetter(OpenGuiComponent::page)
    ).apply(instance, OpenGuiComponent::new));
}
