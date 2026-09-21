package dev.toolkitmc.guiapi.gui;

/**
 * Pure-Java helper for comparing dimension ids without depending on the exact
 * {@code ResourceKey} accessor name of a given Minecraft version.
 *
 * {@code ResourceKey.toString()} renders as
 * {@code ResourceKey[minecraft:dimension / minecraft:the_nether]}; the old code
 * compared that whole string against a bare id, so {@code in_dimension} could
 * never be true.
 */
final class DimensionUtil {

    private DimensionUtil() {}

    /** Extracts the value id ({@code minecraft:the_nether}) from a ResourceKey string. */
    static String extractId(String resourceKeyString) {
        if (resourceKeyString == null) return "";
        int slash = resourceKeyString.lastIndexOf(" / ");
        int close = resourceKeyString.lastIndexOf(']');
        if (slash >= 0 && close > slash) {
            return resourceKeyString.substring(slash + 3, close).trim();
        }
        return resourceKeyString.trim();
    }

    /** True when the key string denotes the same id as {@code wanted}. */
    static boolean matches(String resourceKeyString, String wanted) {
        if (wanted == null || wanted.isBlank()) return false;
        String w = wanted.trim();
        if (!w.contains(":")) w = "minecraft:" + w;
        return extractId(resourceKeyString).equals(w);
    }
}
