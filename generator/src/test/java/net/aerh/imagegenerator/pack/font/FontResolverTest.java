package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontResolverTest {

    private static FontResolver.DefinitionLookup lookup(Map<String, List<FontProviderDefinition>> fonts) {
        return fontId -> Optional.ofNullable(fonts.get(fontId));
    }

    private static FontProviderDefinition.Space space(int codePoint, float advance) {
        return new FontProviderDefinition.Space(Map.of(codePoint, advance), FontFilter.none());
    }

    private static FontProviderDefinition.Space spaceWithFilter(int codePoint, FontFilter filter) {
        return new FontProviderDefinition.Space(Map.of(codePoint, 1.0f), filter);
    }

    private static FontProviderDefinition.Reference reference(String id) {
        return new FontProviderDefinition.Reference(id, FontFilter.none());
    }

    @Test
    void expandsReferenceInlineAtItsPosition() {
        FontProviderDefinition first = space('a', 1.0f);
        FontProviderDefinition innerFirst = space('b', 2.0f);
        FontProviderDefinition innerSecond = space('c', 3.0f);
        FontProviderDefinition last = space('d', 4.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:root", lookup(Map.of(
            "test:root", List.of(first, reference("test:inner"), last),
            "test:inner", List.of(innerFirst, innerSecond))));
        assertEquals(List.of(first, innerFirst, innerSecond, last), resolved);
    }

    @Test
    void expandsNestedReferences() {
        FontProviderDefinition leaf = space('z', 9.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(reference("test:b")),
            "test:b", List.of(reference("test:c")),
            "test:c", List.of(leaf))));
        assertEquals(List.of(leaf), resolved);
    }

    @Test
    void bareReferenceIdDefaultsToMinecraftNamespace() {
        FontProviderDefinition leaf = space('z', 9.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("other", FontFilter.none())),
            "minecraft:other", List.of(leaf))));
        assertEquals(List.of(leaf), resolved);
    }

    @Test
    void bareRootIdDefaultsToMinecraftNamespace() {
        FontProviderDefinition leaf = space('z', 9.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("solo",
            lookup(Map.of("minecraft:solo", List.of(leaf))));
        assertEquals(List.of(leaf), resolved);
    }

    @Test
    void missingRootFails() {
        assertThrows(PackResolveException.class,
            () -> FontResolver.resolveProviders("test:nope", lookup(Map.of())));
    }

    @Test
    void missingReferenceTargetFails() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> FontResolver.resolveProviders("test:a", lookup(Map.of(
                "test:a", List.of(reference("test:nope"))))));
        assertTrue(exception.getMessage().contains("test:nope"));
    }

    @Test
    void twoFontCycleFails() {
        assertThrows(PackResolveException.class, () -> FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(reference("test:b")),
            "test:b", List.of(reference("test:a"))))));
    }

    @Test
    void selfReferenceFails() {
        assertThrows(PackResolveException.class, () -> FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(reference("test:a"))))));
    }

    @Test
    void sameFontReferencedTwiceAtSiblingPositionsIsNotACycle() {
        FontProviderDefinition leaf = space('z', 9.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(reference("test:b"), reference("test:b")),
            "test:b", List.of(leaf))));
        assertEquals(List.of(leaf, leaf), resolved);
    }

    @Test
    void uniformTrueProvidersAreDropped() {
        FontProviderDefinition kept = spaceWithFilter('a', new FontFilter(false, null));
        FontProviderDefinition alsoKept = space('b', 1.0f);
        FontProviderDefinition dropped = spaceWithFilter('c', new FontFilter(true, null));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(kept, dropped, alsoKept))));
        assertEquals(List.of(kept, alsoKept), resolved);
    }

    @Test
    void jpTrueProvidersAreDropped() {
        FontProviderDefinition kept = spaceWithFilter('a', new FontFilter(null, false));
        FontProviderDefinition dropped = spaceWithFilter('b', new FontFilter(null, true));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(dropped, kept))));
        assertEquals(List.of(kept), resolved);
    }

    @Test
    void outerFalseOverridesInnerTrueAndKeepsProvider() {
        // The reference entry's uniform:false overrides the inner provider's uniform:true.
        FontProviderDefinition inner = spaceWithFilter('a', new FontFilter(true, null));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("test:b", new FontFilter(false, null))),
            "test:b", List.of(inner))));
        assertEquals(1, resolved.size());
        assertEquals(new FontFilter(false, null), resolved.get(0).filter());
    }

    @Test
    void outerTrueOverridesInnerAbsentAndDropsProvider() {
        FontProviderDefinition inner = space('a', 1.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("test:b", new FontFilter(true, null))),
            "test:b", List.of(inner))));
        assertTrue(resolved.isEmpty());
    }

    @Test
    void outerKeyMergesAcrossNestedReferences() {
        // uniform:false travels from the outermost reference through a nested one.
        FontProviderDefinition inner = spaceWithFilter('a', new FontFilter(true, null));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("test:b", new FontFilter(false, null))),
            "test:b", List.of(reference("test:c")),
            "test:c", List.of(inner))));
        assertEquals(1, resolved.size());
    }

    @Test
    void innerKeySurvivesWhenOuterKeyAbsent() {
        // The outer filter only sets jp; the inner uniform:true still drops the provider.
        FontProviderDefinition inner = spaceWithFilter('a', new FontFilter(true, null));
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("test:b", new FontFilter(null, false))),
            "test:b", List.of(inner))));
        assertTrue(resolved.isEmpty());
    }

    @Test
    void missingTargetFailsEvenWhenReferenceWouldBeFilteredOut() {
        // Vanilla expands references at load time, before filters apply: a dropped-by-filter
        // reference to a missing font still fails the whole font id.
        assertThrows(PackResolveException.class, () -> FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(new FontProviderDefinition.Reference("test:nope", new FontFilter(true, null)))))));
    }
}
