package net.aerh.imagegenerator.text;

import java.awt.Color;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Data-driven text color replacement equivalent to a resource pack's core text shader color
 * swap: exact-match foreground RGB pairs, with shadow colors derived per vanilla's quartering
 * ({@code (rgb >> 2) & 0x3F3F3F}) unless explicitly overridden. Colors that match no entry
 * pass through unchanged. The library never reads or parses GLSL; consumers own the table.
 *
 * <p>Value semantics (equals/hashCode/toString over sorted entries) are load-bearing: the
 * reflective render cache key stringifies this object, so equal tables must produce equal keys.</p>
 */
public final class TextColorRemap {

    private record Entry(int foregroundTo, Integer shadowTo) {
    }

    private final SortedMap<Integer, Entry> entries;

    private TextColorRemap(SortedMap<Integer, Entry> entries) {
        this.entries = Collections.unmodifiableSortedMap(entries);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Returns the replacement for a foreground color, or the original when unmapped. Alpha is preserved. */
    public Color foreground(Color original) {
        Entry entry = entries.get(original.getRGB() & 0xFFFFFF);
        if (entry == null) {
            return original;
        }
        return withAlpha(entry.foregroundTo(), original.getAlpha());
    }

    /**
     * Returns the replacement shadow for text whose original foreground is {@code originalForeground},
     * or the original shadow when the foreground is unmapped. Alpha is preserved.
     */
    public Color shadow(Color originalForeground, Color originalShadow) {
        Entry entry = entries.get(originalForeground.getRGB() & 0xFFFFFF);
        if (entry == null) {
            return originalShadow;
        }
        int target = entry.shadowTo() != null ? entry.shadowTo() : quarter(entry.foregroundTo());
        return withAlpha(target, originalShadow.getAlpha());
    }

    private static Color withAlpha(int rgb, int alpha) {
        return new Color(alpha << 24 | rgb, true);
    }

    private static int quarter(int rgb) {
        return (rgb >> 2) & 0x3F3F3F;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof TextColorRemap remap && entries.equals(remap.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder("TextColorRemap{");
        entries.forEach((from, entry) -> {
            builder.append(String.format("%06x->%06x", from, entry.foregroundTo()));
            if (entry.shadowTo() != null) {
                builder.append(String.format("/%06x", entry.shadowTo()));
            }
            builder.append(';');
        });
        return builder.append('}').toString();
    }

    public static final class Builder {

        private final SortedMap<Integer, Entry> entries = new TreeMap<>();

        private Builder() {
        }

        /** Maps a foreground RGB to a replacement; the shadow is derived as replacement quartered. */
        public Builder remap(int foregroundFrom, int foregroundTo) {
            return put(foregroundFrom, foregroundTo, null);
        }

        /** Maps a foreground RGB to a replacement with an explicit shadow color. */
        public Builder remap(int foregroundFrom, int foregroundTo, int shadowTo) {
            requireRgb(shadowTo, "shadowTo");
            return put(foregroundFrom, foregroundTo, shadowTo);
        }

        private Builder put(int foregroundFrom, int foregroundTo, Integer shadowTo) {
            requireRgb(foregroundFrom, "foregroundFrom");
            requireRgb(foregroundTo, "foregroundTo");
            if (entries.putIfAbsent(foregroundFrom, new Entry(foregroundTo, shadowTo)) != null) {
                throw new IllegalArgumentException(
                    String.format("Duplicate remap entry for foreground #%06X", foregroundFrom));
            }
            return this;
        }

        public TextColorRemap build() {
            return new TextColorRemap(new TreeMap<>(entries));
        }

        private static void requireRgb(int value, String name) {
            if (value < 0 || value > 0xFFFFFF) {
                throw new IllegalArgumentException(
                    name + " must be an RGB value between 0x000000 and 0xFFFFFF, got " + value);
            }
        }
    }
}
