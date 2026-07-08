package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceRefTest {

    @Test
    void parsesNamespacedRef() {
        ResourceRef ref = ResourceRef.parse("hypixel_skyblock:item/jacob/cactus_knife", null);
        assertEquals("hypixel_skyblock", ref.namespace());
        assertEquals("item/jacob/cactus_knife", ref.path());
    }

    @Test
    void appliesDefaultNamespaceWhenAbsent() {
        ResourceRef ref = ResourceRef.parse("item/paper", "minecraft");
        assertEquals("minecraft", ref.namespace());
        assertEquals("item/paper", ref.path());
    }

    @Test
    void rejectsBareRefWithoutDefaultNamespace() {
        assertThrows(IllegalArgumentException.class, () -> ResourceRef.parse("item/paper", null));
    }

    @Test
    void mapsAssetPaths() {
        ResourceRef ref = ResourceRef.parse("testpack:item/simple", null);
        assertEquals("assets/testpack/items/item/simple.json", ref.itemDefinitionPath());
        assertEquals("assets/testpack/models/item/simple.json", ref.modelPath());
        assertEquals("assets/testpack/textures/item/simple.png", ref.texturePath());
        assertEquals("testpack:item/simple", ref.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ns:", ":path", "ns:a:b", "NS:path", "ns:pa th", "ns:../escape", "ns:a//b", "ns:/abs",
            "..:path", "a..b:x"})
    void rejectsMalformedRefs(String input) {
        assertThrows(IllegalArgumentException.class, () -> ResourceRef.parse(input, null));
    }
}
