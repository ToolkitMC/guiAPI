package dev.toolkitmc.guiapi.gui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DimensionUtilTest {
    private static final String NETHER = "ResourceKey[minecraft:dimension / minecraft:the_nether]";

    @Test void matchesFullId()       { assertTrue(DimensionUtil.matches(NETHER, "minecraft:the_nether")); }
    @Test void matchesBareId()       { assertTrue(DimensionUtil.matches(NETHER, "the_nether")); }
    @Test void rejectsOtherDim()     { assertFalse(DimensionUtil.matches(NETHER, "minecraft:overworld")); }
    @Test void customNamespace()     { assertTrue(DimensionUtil.matches("ResourceKey[minecraft:dimension / pack:mine]", "pack:mine")); }
    @Test void blankWantedIsFalse()  { assertFalse(DimensionUtil.matches(NETHER, "  ")); }
    @Test void extractPlainString()  { assertEquals("minecraft:overworld", DimensionUtil.extractId("minecraft:overworld")); }
}
