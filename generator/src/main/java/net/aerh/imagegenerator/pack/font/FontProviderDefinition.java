package net.aerh.imagegenerator.pack.font;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One parsed provider entry of a pack font JSON ({@code assets/<ns>/font/<path>.json}). Bitmap
 * and space providers render; reference providers expand inline during
 * {@link FontResolver#resolveProviders resolution}; ttf, unihex and legacy_unicode providers
 * parse (so fonts containing them still load) but are unsupported for rendering in this library
 * and never claim a codepoint.
 */
public sealed interface FontProviderDefinition
    permits FontProviderDefinition.Bitmap, FontProviderDefinition.Space,
    FontProviderDefinition.Reference, FontProviderDefinition.Ttf,
    FontProviderDefinition.Unsupported {

    /** The provider's filter; {@link FontFilter#none()} when the JSON carries no filter object. */
    FontFilter filter();

    /** A copy of this definition with the given filter (used for outer-over-inner filter merging). */
    FontProviderDefinition withFilter(FontFilter filter);

    /**
     * A {@code bitmap} provider: a glyph sheet texture cut into equal cells by a codepoint grid.
     *
     * @param file     texture file reference INCLUDING the extension (e.g.
     *                 {@code minecraft:font/ascii.png}), resolved beneath
     *                 {@code assets/<ns>/textures/}
     * @param height   GUI-pixel height each cell renders at (default 8; vanilla accepts any int)
     * @param ascent   GUI pixels of the cell above the baseline; {@code ascent <= height} is
     *                 validated at parse time, negative values are legal
     * @param charRows rows of the codepoint grid, equal codepoint count per row; U+0000 entries
     *                 mark unused cells and are skipped entirely
     * @throws IllegalArgumentException when {@code charRows} is empty, contains an empty row, or
     *                                  contains rows of unequal codepoint counts - validated here
     *                                  so every construction path (not just
     *                                  {@code PackFontParser}) fails with a diagnostic instead of
     *                                  an out-of-bounds crash while cutting the sheet
     */
    record Bitmap(String file, int height, int ascent, List<String> charRows, FontFilter filter)
        implements FontProviderDefinition {

        public Bitmap {
            charRows = List.copyOf(charRows);
            if (charRows.isEmpty()) {
                throw new IllegalArgumentException("Bitmap font charRows must not be empty");
            }
            int expectedCodePoints = charRows.get(0).codePointCount(0, charRows.get(0).length());
            if (expectedCodePoints == 0) {
                throw new IllegalArgumentException("Bitmap font charRows rows must not be empty strings");
            }
            for (int i = 1; i < charRows.size(); i++) {
                String row = charRows.get(i);
                int codePoints = row.codePointCount(0, row.length());
                if (codePoints != expectedCodePoints) {
                    throw new IllegalArgumentException(
                        "Bitmap font charRows must have equal codepoint counts: row " + i + " has "
                            + codePoints + ", expected " + expectedCodePoints);
                }
            }
        }

        @Override
        public Bitmap withFilter(FontFilter filter) {
            return new Bitmap(file, height, ascent, charRows, filter);
        }
    }

    /**
     * A {@code space} provider: codepoint to advance in GUI pixels. Advances are floats; negative
     * and fractional values are legal. Space glyphs draw nothing (no glyph, no shadow, no bold
     * copy) but their advance fully counts toward text width.
     */
    record Space(Map<Integer, Float> advances, FontFilter filter) implements FontProviderDefinition {

        public Space {
            advances = Map.copyOf(advances);
        }

        @Override
        public Space withFilter(FontFilter filter) {
            return new Space(advances, filter);
        }
    }

    /**
     * A {@code reference} provider: expands the referenced font's provider list inline at this
     * position during resolution. A bare id defaults to the {@code minecraft} namespace.
     */
    record Reference(String id, FontFilter filter) implements FontProviderDefinition {

        @Override
        public Reference withFilter(FontFilter filter) {
            return new Reference(id, filter);
        }
    }

    /**
     * A {@code ttf} provider. Parsed for completeness (a font whose only providers are TTF still
     * loads) but unsupported for rendering: it never claims a codepoint, letting later providers
     * serve it. {@code skip} is the set of codepoints the TTF explicitly excludes.
     */
    record Ttf(String file, float size, float oversample, float shiftX, float shiftY,
               Set<Integer> skip, FontFilter filter) implements FontProviderDefinition {

        public Ttf {
            skip = Set.copyOf(skip);
        }

        @Override
        public Ttf withFilter(FontFilter filter) {
            return new Ttf(file, size, oversample, shiftX, shiftY, skip, filter);
        }
    }

    /**
     * A parsed-but-unrenderable provider ({@code unihex} or {@code legacy_unicode}). Never claims
     * a codepoint; {@link PackFont#hasUnsupportedProviderFor} reports its presence so callers can
     * log the gap once.
     */
    record Unsupported(String type, FontFilter filter) implements FontProviderDefinition {

        @Override
        public Unsupported withFilter(FontFilter filter) {
            return new Unsupported(type, filter);
        }
    }
}
