package net.aerh.imagegenerator.parser;

import net.aerh.imagegenerator.pack.PackId;
import org.jetbrains.annotations.Nullable;

/**
 * Carries per-render context through the placeholder parsing pipeline so parsers can resolve
 * pack-conditional data (e.g. {@code packOverrides} on icons and stats).
 * <p>
 * A {@code null} pack ID (or {@link PackId#VANILLA}) means no pack-specific overrides apply.
 */
public record ParseContext(@Nullable PackId packId) {

    private static final ParseContext EMPTY = new ParseContext(null);

    /**
     * @return a context with no active pack; placeholders resolve to their base characters
     */
    public static ParseContext empty() {
        return EMPTY;
    }

    /**
     * Creates a context for the given pack. Vanilla is normalized to {@link #empty()} since the
     * vanilla pack never carries overrides.
     *
     * @param packId the active pack, or {@code null} for none
     *
     * @return the parse context
     */
    public static ParseContext of(@Nullable PackId packId) {
        if (!PackId.isActive(packId)) {
            return EMPTY;
        }
        return new ParseContext(packId);
    }
}
