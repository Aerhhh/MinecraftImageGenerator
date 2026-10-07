package net.aerh.imagegenerator.parser;

import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.pack.PackLineage;
import org.jetbrains.annotations.Nullable;

/**
 * Carries per-render context through the placeholder parsing pipeline so parsers can resolve
 * pack-conditional data (e.g. {@code packOverrides} on icons and stats).
 * <p>
 * A {@code null} pack (or one whose id is {@link PackId#VANILLA}) means no pack-specific overrides
 * apply. A pack registered as a variant of another resolves overrides through its whole
 * {@link PackLineage}.
 */
public record ParseContext(@Nullable PackLineage pack) {

    private static final ParseContext EMPTY = new ParseContext(null);

    /**
     * @return a context with no active pack; placeholders resolve to their base characters
     */
    public static ParseContext empty() {
        return EMPTY;
    }

    /**
     * Creates a context for the given pack with no variant. Vanilla is normalized to
     * {@link #empty()} since the vanilla pack never carries overrides.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the parse context
     */
    public static ParseContext of(@Nullable PackId packId) {
        return of(packId == null ? null : PackLineage.of(packId));
    }

    /**
     * Creates a context for the given pack lineage. Vanilla is normalized to {@link #empty()}
     * since the vanilla pack never carries overrides.
     *
     * @param pack the active pack's lineage, or {@code null} for none
     *
     * @return the parse context
     */
    public static ParseContext of(@Nullable PackLineage pack) {
        if (pack == null || !PackId.isActive(pack.id())) {
            return EMPTY;
        }
        return new ParseContext(pack);
    }
}
