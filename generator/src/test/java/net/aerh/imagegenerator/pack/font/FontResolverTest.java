package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

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

    /** A single-reference chain: {@code test:c0 -> test:c1 -> ... -> test:c<length>} -> leaf. */
    private static Map<String, List<FontProviderDefinition>> chain(int length) {
        Map<String, List<FontProviderDefinition>> fonts = new HashMap<>();
        for (int i = 0; i < length; i++) {
            fonts.put("test:c" + i, List.of(reference("test:c" + (i + 1))));
        }
        fonts.put("test:c" + length, List.of(space('z', 1.0f)));
        return fonts;
    }

    @Test
    void referenceChainAtTheDepthCapResolves() {
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:c0",
            lookup(chain(FontResolver.MAX_REFERENCE_DEPTH)));
        assertEquals(1, resolved.size());
    }

    @Test
    void referenceChainOverTheDepthCapFails() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> FontResolver.resolveProviders("test:c0",
                lookup(chain(FontResolver.MAX_REFERENCE_DEPTH + 1))));
        assertTrue(exception.getMessage().contains("deeper than"), exception.getMessage());
    }

    /** {@code fanOut} references to one target of {@code providersPerTarget} space providers. */
    private static Map<String, List<FontProviderDefinition>> fanOut(int fanOut, int providersPerTarget) {
        List<FontProviderDefinition> targets = new ArrayList<>();
        for (int i = 0; i < providersPerTarget; i++) {
            targets.add(space('a' + i, 1.0f));
        }
        List<FontProviderDefinition> references = new ArrayList<>();
        for (int i = 0; i < fanOut; i++) {
            references.add(reference("test:wide"));
        }
        return Map.of("test:root", List.copyOf(references), "test:wide", List.copyOf(targets));
    }

    @Test
    void wideFanOutAtTheProviderCapResolves() {
        // 64 references x 64 providers = exactly MAX_RESOLVED_PROVIDERS.
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:root",
            lookup(fanOut(64, 64)));
        assertEquals(FontResolver.MAX_RESOLVED_PROVIDERS, resolved.size());
    }

    @Test
    void wideFanOutOverTheProviderCapFails() {
        PackResolveException exception = assertThrows(PackResolveException.class,
            () -> FontResolver.resolveProviders("test:root", lookup(fanOut(64, 65))));
        assertTrue(exception.getMessage().contains("more than"), exception.getMessage());
    }

    @Test
    void doublingDiamondGraphResolvesWithOneLookupPerFont() {
        // d1 references d2 twice, d2 references d3 twice, ... d11 holds the leaf: the output
        // doubles per level (2^10 = 1024 leaves) but memoization must look every font up
        // exactly once - without it, crafted packs get exponential lookup work.
        Map<String, List<FontProviderDefinition>> fonts = new HashMap<>();
        for (int i = 1; i < 11; i++) {
            fonts.put("test:d" + i, List.of(reference("test:d" + (i + 1)), reference("test:d" + (i + 1))));
        }
        fonts.put("test:d11", List.of(space('z', 1.0f)));
        AtomicInteger lookups = new AtomicInteger();
        FontResolver.DefinitionLookup countingLookup = fontId -> {
            lookups.incrementAndGet();
            return Optional.ofNullable(fonts.get(fontId));
        };
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:d1", countingLookup);
        assertEquals(1024, resolved.size());
        assertEquals(11, lookups.get(), "each font resolves through exactly one lookup");
    }

    @Test
    void memoizedExpansionKeepsFilterSensitivity() {
        // The same target referenced under two DIFFERENT outer filters must expand per filter:
        // a memo keyed on the id alone would leak the first filter into the second expansion.
        FontProviderDefinition inner = space('a', 1.0f);
        List<FontProviderDefinition> resolved = FontResolver.resolveProviders("test:a", lookup(Map.of(
            "test:a", List.of(
                new FontProviderDefinition.Reference("test:b", new FontFilter(true, null)),
                new FontProviderDefinition.Reference("test:b", new FontFilter(false, null))),
            "test:b", List.of(inner))));
        // The uniform:true occurrence is dropped by the filter pass; the uniform:false one stays.
        assertEquals(1, resolved.size());
        assertEquals(new FontFilter(false, null), resolved.get(0).filter());
    }
}
