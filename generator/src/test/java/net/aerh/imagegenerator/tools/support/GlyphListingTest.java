package net.aerh.imagegenerator.tools.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlyphListingTest {

    private static String rowFor(String name) {
        return GlyphListing.buildRows(null).stream()
            .filter(row -> row.startsWith("`%%" + name + "%%`"))
            .findFirst()
            .orElseThrow(() -> new AssertionError("no row for " + name));
    }

    @Test
    void statRowsShowBaseCharAndPackOverrideCodepoint() {
        String row = rowFor("strength");

        assertTrue(row.contains("\u2741"), row);
        assertTrue(row.contains("hypixel:skyblock"), row);
        assertTrue(row.contains("U+E00D"), row);
    }

    @Test
    void packOnlyGlyphsShowCodepointsInsteadOfTofu() {
        assertEquals("`%%mob_undead%%` U+E084", rowFor("mob_undead"));
        assertEquals("`%%hypixel_staff%%` U+12DE", rowFor("hypixel_staff"));
    }

    @Test
    void classicCharactersShowThemselves() {
        assertEquals("`%%dot%%` \u2022", rowFor("dot"));
    }

    @Test
    void filterNarrowsByNameCaseInsensitively() {
        List<String> rows = GlyphListing.buildRows("FORTUNE");

        assertFalse(rows.isEmpty());
        assertTrue(rows.stream().allMatch(row -> row.toLowerCase().contains("fortune")), rows.toString());
        assertTrue(GlyphListing.buildRows("definitely_not_a_placeholder").isEmpty());
    }

    @Test
    void rowsAreSortedByName() {
        List<String> rows = GlyphListing.buildRows(null);
        List<String> sorted = rows.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();

        assertEquals(sorted, rows);
    }

    @Test
    void pageJoinsRowsUnderTheUsageHeader() {
        String page = GlyphListing.buildPage(List.of("row one", "row two"));

        assertEquals(GlyphListing.DEFAULT_HEADER + "\nrow one\nrow two", page);
    }

    @Test
    void buildPageUsesTheGivenHeader() {
        String page = GlyphListing.buildPage("Custom header", List.of("row one", "row two"));

        assertEquals("Custom header\nrow one\nrow two", page);
    }

    @Test
    void defaultHeaderDoesNotMentionDiscord() {
        assertEquals("Use `%%name%%` in generator text. Pack glyphs are shown as codepoints; generated images render them.",
            GlyphListing.DEFAULT_HEADER);
    }
}
