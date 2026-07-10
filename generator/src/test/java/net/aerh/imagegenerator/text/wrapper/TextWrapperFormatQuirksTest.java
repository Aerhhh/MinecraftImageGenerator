package net.aerh.imagegenerator.text.wrapper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the two load-bearing wrapper quirks that the shared {@code CodeClassifier} must not
 * change: reset codes accumulate like styles when carrying formatting across wrapped lines,
 * and font codes count as visible characters when measuring line width. Existing wrapped-line
 * expectations depend on both, so any change here is a deliberate output change, not a
 * refactor.
 */
class TextWrapperFormatQuirksTest {

    @Test
    void resetAccumulatesLikeAStyleAcrossWrappedLines() {
        // A true reset would clear the carried color; the wrapper instead keeps "&c" and
        // appends "&r" to the formatting codes, so both are replayed on the next line.
        List<String> lines = TextWrapper.wrapString("&c&rAAAA BBBB", 4);
        assertEquals(List.of("&c&rAAAA", "&c&rBBBB"), lines);
    }

    @Test
    void resetIsZeroWidthWhenSplittingLongWordsAndCarriesOver() {
        List<String> lines = TextWrapper.wrapString("&rABCDE", 4);
        assertEquals(List.of("&rABCD", "&rE"), lines);
    }

    @Test
    void formattingCodesKeepTheirOriginalCaseWhenCarriedOver() {
        // Dedup of carried formatting codes is case-sensitive on the original spelling.
        List<String> lines = TextWrapper.wrapString("&L&lAA BB", 2);
        assertEquals(List.of("&L&lAA", "&L&lBB"), lines);
    }

    @Test
    void fontCodesCountAsVisibleCharactersWhenWrapping() {
        // "&gA" measures three visible characters, unlike GradientParser which treats font
        // codes as zero-width when counting gradient positions.
        List<String> lines = TextWrapper.wrapString("&gA B", 3);
        assertEquals(List.of("&gA", "B"), lines);
    }

    @Test
    void fontCodesDoNotCarryOverAsFormatting() {
        List<String> lines = TextWrapper.wrapString("&gAAA BBB", 3);
        assertEquals(List.of("&gA", "AA", "BBB"), lines,
            "the split segments after a font code must not replay it");
    }

    @Test
    void stripColorCodesLeavesFontCodesVisible() {
        assertEquals("&gHi &hthere", TextWrapper.stripColorCodes("&gHi &hthere"));
    }

    @Test
    void stripColorCodesRemovesColorsStylesAndReset() {
        assertEquals("X", TextWrapper.stripColorCodes("&c&l&r&#ff00aaX"));
        assertEquals("X", TextWrapper.stripColorCodes("&C&L&R§#FF00AAX"), "case-insensitive");
    }

    @Test
    void stripColorCodesScansPastDoubledSymbols() {
        assertEquals("&", TextWrapper.stripColorCodes("&&l"),
            "the first symbol is literal; the second starts the style code");
    }
}
