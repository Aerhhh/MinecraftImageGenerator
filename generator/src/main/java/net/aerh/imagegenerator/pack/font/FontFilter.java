package net.aerh.imagegenerator.pack.font;

/**
 * The optional {@code filter} object any font provider entry may carry, restricting the provider
 * to specific client font options ({@code uniform} = Force Unicode, {@code jp} = Japanese glyph
 * variants). A {@code null} field means the key is absent from the JSON, which is distinct from
 * an explicit {@code false}: an absent key matches any option state, while an explicit value
 * requires that exact state.
 */
public record FontFilter(Boolean uniform, Boolean jp) {

    private static final FontFilter NONE = new FontFilter(null, null);

    /** The empty filter (no keys present); providers carrying it are always kept. */
    public static FontFilter none() {
        return NONE;
    }

    /**
     * Merges this filter (the OUTER filter, e.g. the one on a reference provider entry) over an
     * inner provider's filter: keys present on this filter win, absent keys fall through to the
     * inner filter. Mirrors the vanilla merge direction where the referencing entry's filter
     * overrides the referenced font's provider filters.
     */
    public FontFilter mergedOver(FontFilter inner) {
        return new FontFilter(uniform != null ? uniform : inner.uniform, jp != null ? jp : inner.jp);
    }

    /**
     * Whether a provider carrying this filter is kept under this library's fixed render options:
     * Force Unicode OFF and jp OFF. Providers requiring {@code uniform == true} or
     * {@code jp == true} are dropped; absent or {@code false} keys keep the provider.
     */
    public boolean keptForDefaultOptions() {
        return !Boolean.TRUE.equals(uniform) && !Boolean.TRUE.equals(jp);
    }
}
