package net.aerh.imagegenerator.parser.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@code %%gradient:#RRGGBB:#RRGGBB[:...]%%body%%/gradient%%} placeholder expands into
 * per-character {@code &#rrggbb} codes. Reference behavior is RGBirdflop's defaults
 * (https://www.birdflop.com/resources/rgb/), verified 2026-07-10; tests marked
 * "Birdflop vector" mirror its output byte-for-byte (modulo canonical lowercase hex).
 */
class GradientParserTest {

    private final GradientParser parser = new GradientParser();

    @Test
    void twoStopGradientMatchesBirdflopVector() {
        // Birdflop vector: spaces advance the position (t = i / (len - 1) over all
        // characters) but emit no codes.
        assertEquals("&#000000A&#404040B &#bfbfbfC&#ffffffD&r",
            parser.parse("%%gradient:#000000:#ffffff%%AB CD%%/gradient%%"));
    }

    @Test
    void threeStopGradientMatchesBirdflopVector() {
        // Birdflop vector: stops evenly spaced, piecewise linear between adjacent stops.
        assertEquals("&#000000A&#808080B &#accfd8C&#599fb0D&r",
            parser.parse("%%gradient:#000000:#ffffff:#599fb0%%AB CD%%/gradient%%"));
    }

    @Test
    void singleCharacterBodyGetsTheFirstStop() {
        assertEquals("&#ff0000A&r",
            parser.parse("%%gradient:#ff0000:#0000ff%%A%%/gradient%%"));
    }

    @Test
    void emptyBodyExpandsToNothing() {
        assertEquals("", parser.parse("%%gradient:#ff0000:#0000ff%%%%/gradient%%"));
    }

    @Test
    void whitespaceOnlyBodyEmitsNoCodes() {
        assertEquals("   ", parser.parse("%%gradient:#ff0000:#0000ff%%   %%/gradient%%"));
    }

    @Test
    void tagNameAndHexDigitsAreCaseInsensitive() {
        assertEquals("&#ff0000A&#0000ffB&r",
            parser.parse("%%GRADIENT:#FF0000:#0000FF%%AB%%/GRADIENT%%"));
    }

    @Test
    void surroundingTextIsPreserved() {
        assertEquals("pre &#000000A&#ffffffB&r post",
            parser.parse("pre %%gradient:#000000:#ffffff%%AB%%/gradient%% post"));
    }

    @Test
    void priorColorIsRestoredAfterTheCloser() {
        assertEquals("&6Gold &#000000A&#ffffffB&r&6 tail",
            parser.parse("&6Gold %%gradient:#000000:#ffffff%%AB%%/gradient%% tail"));
    }

    @Test
    void priorColorAndFormattingAreRestoredAfterTheCloser() {
        assertEquals("&6&lGold &#000000A&#ffffffB&r&6&l tail",
            parser.parse("&6&lGold %%gradient:#000000:#ffffff%%AB%%/gradient%% tail"));
    }

    @Test
    void resetBeforeTheOpenerClearsTheRestoredState() {
        assertEquals("&6Gold &rplain &#000000A&#ffffffB&r tail",
            parser.parse("&6Gold &rplain %%gradient:#000000:#ffffff%%AB%%/gradient%% tail"));
    }

    @Test
    void multipleGradientsInOneLineExpandIndependently() {
        assertEquals("&#000000A&#ffffffB&r &#ff0000C&#0000ffD&r",
            parser.parse("%%gradient:#000000:#ffffff%%AB%%/gradient%% %%gradient:#ff0000:#0000ff%%CD%%/gradient%%"));
    }

    @Test
    void invalidHexStopsStayLiteral() {
        assertEquals("%%gradient:#ff000:#0000ff%%AB%%/gradient%%",
            parser.parse("%%gradient:#ff000:#0000ff%%AB%%/gradient%%"), "five digits");
        assertEquals("%%gradient:#zzzzzz:#0000ff%%AB%%/gradient%%",
            parser.parse("%%gradient:#zzzzzz:#0000ff%%AB%%/gradient%%"), "non-hex digits");
        assertEquals("%%gradient:#ff00001:#0000ff%%AB%%/gradient%%",
            parser.parse("%%gradient:#ff00001:#0000ff%%AB%%/gradient%%"), "seven digits");
    }

    @Test
    void singleStopStaysLiteral() {
        assertEquals("%%gradient:#ff0000%%AB%%/gradient%%",
            parser.parse("%%gradient:#ff0000%%AB%%/gradient%%"));
    }

    @Test
    void unclosedGradientStaysLiteral() {
        assertEquals("%%gradient:#ff0000:#0000ff%%AB",
            parser.parse("%%gradient:#ff0000:#0000ff%%AB"));
    }

    @Test
    void closerWithoutOpenerStaysLiteral() {
        assertEquals("AB%%/gradient%%CD", parser.parse("AB%%/gradient%%CD"));
    }

    @Test
    void unclosedOpenerAfterAValidPairStaysLiteral() {
        assertEquals("&#000000A&#ffffffB&r%%gradient:#ff0000:#0000ff%%CD",
            parser.parse("%%gradient:#000000:#ffffff%%AB%%/gradient%%%%gradient:#ff0000:#0000ff%%CD"));
    }

    @Test
    void nestedOpenerInsideABodyIsLiteralColoredText() {
        // No nesting: the body ends at the first closer, so an opener inside it is just text.
        String out = parser.parse(
            "%%gradient:#ff0000:#0000ff%%X%%gradient:#00ff00:#112233%%Y%%/gradient%%");
        assertTrue(out.startsWith("&#ff0000X"), "outer gradient starts at its first stop");
        assertTrue(out.endsWith("&#0000ffY&r"), "outer gradient ends at its last stop, then restores");
        assertEquals(-1, out.indexOf("%%/gradient%%"), "the single closer is consumed by the outer gradient");
    }
}
