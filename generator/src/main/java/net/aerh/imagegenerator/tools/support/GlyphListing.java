package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.data.Icon;
import net.aerh.imagegenerator.data.Stat;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds the text rows for {@code /gen symbols}: every icon and stat placeholder with its base
 * character and any pack-specific override characters.
 * <p>
 * Chat clients often cannot draw private use area glyphs, so those are printed as U+XXXX
 * codepoints.
 */
public final class GlyphListing {

    private GlyphListing() {
    }

    /**
     * Builds one row per icon and stat placeholder, alphabetically by name.
     *
     * @param filter optional case-insensitive substring to match against placeholder names
     *
     * @return the matching rows, possibly empty
     */
    public static List<String> buildRows(@Nullable String filter) {
        List<String> rows = new ArrayList<>();

        for (Icon icon : Icon.getIcons()) {
            if (matches(icon.getName(), filter)) {
                rows.add(row(icon.getName(), icon.getIcon(), icon.getPackOverrides()));
            }
        }

        for (Stat stat : Stat.getStats()) {
            if (matches(stat.getName(), filter)) {
                rows.add(row(stat.getName(), stat.getIcon(), stat.getPackOverrides()));
            }
        }

        rows.sort(String.CASE_INSENSITIVE_ORDER);
        return rows;
    }

    /** Header used by {@link #buildPage(List)}: neutral wording that fits any client. */
    public static final String DEFAULT_HEADER =
        "Use `%%name%%` in generator text. Pack glyphs are shown as codepoints; generated images render them.";

    /**
     * Joins a page of rows under {@link #DEFAULT_HEADER}.
     */
    public static String buildPage(List<String> rows) {
        return buildPage(DEFAULT_HEADER, rows);
    }

    /**
     * Joins a page of rows under the given header line.
     *
     * @param header the first line of the page
     * @param rows   the rows for the current page
     *
     * @return the page text
     */
    public static String buildPage(String header, List<String> rows) {
        return header + "\n" + String.join("\n", rows);
    }

    private static boolean matches(@Nullable String name, @Nullable String filter) {
        if (name == null) {
            return false;
        }
        if (filter == null || filter.isBlank()) {
            return true;
        }
        return name.toLowerCase(Locale.ROOT).contains(filter.trim().toLowerCase(Locale.ROOT));
    }

    private static String row(String name, @Nullable String baseCharacter, @Nullable Map<String, String> packOverrides) {
        StringBuilder row = new StringBuilder("`%%").append(name).append("%%` ").append(printable(baseCharacter));

        if (packOverrides != null) {
            packOverrides.forEach((packId, character) ->
                row.append(" (").append(packId).append(": ").append(printable(character)).append(")"));
        }

        return row.toString();
    }

    private static String printable(@Nullable String character) {
        if (character == null || character.isEmpty()) {
            return "?";
        }

        int codePoint = character.codePointAt(0);
        boolean unrenderable = (codePoint >= 0xE000 && codePoint <= 0xF8FF) || codePoint == 0x12DE;
        return unrenderable ? "U+%04X".formatted(codePoint) : character;
    }
}
