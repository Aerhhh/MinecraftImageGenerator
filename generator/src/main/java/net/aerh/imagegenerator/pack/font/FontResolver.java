package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.exception.PackResolveException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Flattens a font id to its ordered, render-ready provider list: reference providers expand
 * inline recursively (outer filter keys overriding inner on merge), then providers whose
 * effective filter requires {@code uniform == true} or {@code jp == true} are dropped (this
 * library renders with Force Unicode OFF and jp OFF).
 *
 * <p>Failure scope mirrors vanilla: a missing or cyclic reference fails the WHOLE font id being
 * resolved with {@link PackResolveException}, even when the offending reference entry would have
 * been dropped by its filter afterwards (vanilla expands references at load time, before filters
 * apply).
 */
public final class FontResolver {

    private FontResolver() {
    }

    /** Supplies the parsed provider list for a normalized ({@code ns:path}) font id. */
    @FunctionalInterface
    public interface DefinitionLookup {

        /**
         * @return the font's parsed providers, or empty when the font id does not exist
         * @throws PackResolveException when the font exists but its JSON is malformed
         */
        Optional<List<FontProviderDefinition>> find(String fontId);
    }

    /**
     * Resolves a font id to its flattened, filtered provider list. The first-listed provider in
     * the returned list wins per codepoint (see {@link PackFont#glyph}).
     *
     * @param fontId font id; a bare id defaults to the {@code minecraft} namespace
     * @param lookup source of parsed font definitions, consulted for the root id and every
     *               reference target
     * @throws PackResolveException when the root font, or any referenced font, is missing, or
     *                              when the reference graph contains a cycle
     */
    public static List<FontProviderDefinition> resolveProviders(String fontId, DefinitionLookup lookup) {
        String rootId = normalize(fontId);
        List<FontProviderDefinition> rootDefinitions = lookup.find(rootId)
            .orElseThrow(() -> new PackResolveException("Font `%s` not found", rootId));
        Set<String> visiting = new LinkedHashSet<>();
        visiting.add(rootId);
        List<FontProviderDefinition> expanded = new ArrayList<>();
        expand(rootDefinitions, FontFilter.none(), visiting, lookup, expanded);
        return expanded.stream()
            .filter(definition -> definition.filter().keptForDefaultOptions())
            .toList();
    }

    private static void expand(List<FontProviderDefinition> definitions, FontFilter outerFilter,
                               Set<String> visiting, DefinitionLookup lookup,
                               List<FontProviderDefinition> out) {
        for (FontProviderDefinition definition : definitions) {
            if (definition instanceof FontProviderDefinition.Reference reference) {
                String referencedId = normalize(reference.id());
                FontFilter referenceFilter = outerFilter.mergedOver(reference.filter());
                if (!visiting.add(referencedId)) {
                    throw new PackResolveException("Font reference cycle: %s -> %s",
                        String.join(" -> ", visiting), referencedId);
                }
                List<FontProviderDefinition> referenced = lookup.find(referencedId)
                    .orElseThrow(() -> new PackResolveException(
                        "Font reference target `%s` not found", referencedId));
                expand(referenced, referenceFilter, visiting, lookup, out);
                // Remove after expansion: only the ACTIVE resolution path counts for cycle
                // detection; the same font referenced twice at sibling positions is legal.
                visiting.remove(referencedId);
            } else {
                out.add(definition.withFilter(outerFilter.mergedOver(definition.filter())));
            }
        }
    }

    private static String normalize(String fontId) {
        return fontId.indexOf(':') < 0 ? "minecraft:" + fontId : fontId;
    }
}
