package net.aerh.imagegenerator.pack.font;

import net.aerh.imagegenerator.exception.PackResolveException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 *
 * <p><b>Expansion caps (defensive, beyond vanilla):</b> reference nesting is bounded by
 * {@link #MAX_REFERENCE_DEPTH} and the total expanded provider count by
 * {@link #MAX_RESOLVED_PROVIDERS}; a font exceeding either fails loudly. Within one resolution,
 * repeated references to the same target under the same effective filter expand the target ONCE
 * and reuse the expanded slice, so a crafted pack whose reference graph doubles at every level
 * (a diamond of diamonds) costs work linear in the unique fonts plus the capped output size
 * instead of exponential lookups.
 */
public final class FontResolver {

    /**
     * Maximum reference nesting depth for one font resolution. Vanilla has no explicit bound
     * (cycles aside); real packs nest two or three levels, so 16 is far beyond legitimate use
     * while keeping crafted deep chains from recursing unboundedly.
     */
    public static final int MAX_REFERENCE_DEPTH = 16;

    /**
     * Maximum total providers a single font id may expand to, references included. Real server
     * fonts stay well below a few hundred providers; 4096 leaves generous headroom while
     * bounding the memory and glyph-sheet work a crafted wide fan-out of references can demand.
     */
    public static final int MAX_RESOLVED_PROVIDERS = 4096;

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
     *               reference target (once per distinct target per resolution)
     * @throws PackResolveException when the root font, or any referenced font, is missing, when
     *                              the reference graph contains a cycle, or when the expansion
     *                              exceeds {@link #MAX_REFERENCE_DEPTH} or
     *                              {@link #MAX_RESOLVED_PROVIDERS}
     */
    public static List<FontProviderDefinition> resolveProviders(String fontId, DefinitionLookup lookup) {
        String rootId = normalize(fontId);
        List<FontProviderDefinition> rootDefinitions = lookup.find(rootId)
            .orElseThrow(() -> new PackResolveException("Font `%s` not found", rootId));
        Resolution resolution = new Resolution(rootId, lookup);
        resolution.visiting.add(rootId);
        resolution.expand(rootDefinitions, FontFilter.none(), 0);
        return resolution.expanded.stream()
            .filter(definition -> definition.filter().keptForDefaultOptions())
            .toList();
    }

    /**
     * State of one {@link #resolveProviders} call: the append-only expansion output, the active
     * resolution path for cycle detection, and the per-resolution memo of already-expanded
     * reference targets.
     */
    private static final class Resolution {

        /** Memo key: a reference target under its effective (outer-merged) filter. */
        private record ExpandedReference(String fontId, FontFilter filter) {
        }

        private final String rootId;
        private final DefinitionLookup lookup;
        /** The ACTIVE resolution path only; entries are removed once their expansion returns. */
        private final Set<String> visiting = new LinkedHashSet<>();
        /**
         * Already-expanded reference targets, mapped to their {@code [start, end)} slice of
         * {@link #expanded}. Slices stay valid because the list is append-only.
         */
        private final Map<ExpandedReference, int[]> memo = new HashMap<>();
        private final List<FontProviderDefinition> expanded = new ArrayList<>();

        private Resolution(String rootId, DefinitionLookup lookup) {
            this.rootId = rootId;
            this.lookup = lookup;
        }

        private void expand(List<FontProviderDefinition> definitions, FontFilter outerFilter, int depth) {
            for (FontProviderDefinition definition : definitions) {
                if (definition instanceof FontProviderDefinition.Reference reference) {
                    expandReference(reference, outerFilter, depth);
                } else {
                    append(definition.withFilter(outerFilter.mergedOver(definition.filter())));
                }
            }
        }

        private void expandReference(FontProviderDefinition.Reference reference, FontFilter outerFilter, int depth) {
            String referencedId = normalize(reference.id());
            FontFilter referenceFilter = outerFilter.mergedOver(reference.filter());
            int[] memoized = memo.get(new ExpandedReference(referencedId, referenceFilter));
            if (memoized != null) {
                // The target already expanded under this exact effective filter: re-append its
                // expanded slice instead of walking the reference graph again. A memo hit does
                // no recursion, so it needs no depth check; the append cap still applies.
                appendSlice(memoized);
                return;
            }
            if (depth >= MAX_REFERENCE_DEPTH) {
                throw new PackResolveException(
                    "Font `%s` nests references deeper than %s levels at `%s`",
                    rootId, String.valueOf(MAX_REFERENCE_DEPTH), referencedId);
            }
            if (!visiting.add(referencedId)) {
                throw new PackResolveException("Font reference cycle: %s -> %s",
                    String.join(" -> ", visiting), referencedId);
            }
            List<FontProviderDefinition> referenced = lookup.find(referencedId)
                .orElseThrow(() -> new PackResolveException(
                    "Font reference target `%s` not found", referencedId));
            int start = expanded.size();
            expand(referenced, referenceFilter, depth + 1);
            // Remove after expansion: only the ACTIVE resolution path counts for cycle
            // detection; the same font referenced twice at sibling positions is legal.
            visiting.remove(referencedId);
            memo.put(new ExpandedReference(referencedId, referenceFilter), new int[]{start, expanded.size()});
        }

        private void appendSlice(int[] range) {
            // Copy before appending: appending directly from a live subList view of the list
            // being appended to would throw ConcurrentModificationException.
            for (FontProviderDefinition definition : List.copyOf(expanded.subList(range[0], range[1]))) {
                append(definition);
            }
        }

        private void append(FontProviderDefinition definition) {
            if (expanded.size() >= MAX_RESOLVED_PROVIDERS) {
                throw new PackResolveException(
                    "Font `%s` expands to more than %s providers; refusing to resolve",
                    rootId, String.valueOf(MAX_RESOLVED_PROVIDERS));
            }
            expanded.add(definition);
        }
    }

    private static String normalize(String fontId) {
        return fontId.indexOf(':') < 0 ? "minecraft:" + fontId : fontId;
    }
}
