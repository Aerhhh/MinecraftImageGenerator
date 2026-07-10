package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.text.wrapper.TextWrapper;
import org.junit.jupiter.api.Test;

import java.util.List;

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
    void priorHexColorIsRestoredAfterTheCloser() {
        assertEquals("&#123abcGold &#000000A&#ffffffB&r&#123abc tail",
            parser.parse("&#123abcGold %%gradient:#000000:#ffffff%%AB%%/gradient%% tail"));
    }

    @Test
    void priorFontIsRestoredAfterTheCloser() {
        assertEquals("&gRunic &#000000A&#ffffffB&r&g tail",
            parser.parse("&gRunic %%gradient:#000000:#ffffff%%AB%%/gradient%% tail"));
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

    @Test
    void formattingIsReemittedAfterEveryColorCode() {
        // Birdflop vector (bold enabled): color codes reset formatting, so the active
        // style set must follow every per-character color.
        assertEquals("&#000000&lA&#404040&lB &#bfbfbf&lC&#ffffff&lD&r",
            parser.parse("%%gradient:#000000:#ffffff%%&lAB CD%%/gradient%%"));
    }

    @Test
    void midBodyFormattingAppliesFromThatPointOn() {
        assertEquals("&#000000A&#ffffff&lB&r",
            parser.parse("%%gradient:#000000:#ffffff%%A&lB%%/gradient%%"));
    }

    @Test
    void resetInsideTheBodyClearsFormattingButTheGradientContinues() {
        assertEquals("&#000000&lA&r&#ffffffB&r",
            parser.parse("%%gradient:#000000:#ffffff%%&lA&rB%%/gradient%%"));
    }

    @Test
    void innerNamedColorOverridesTheGradientWithoutRestoration() {
        // C and D still count toward the gradient length (t = i / 3), so B sits at 1/3.
        assertEquals("&#000000A&#555555B&cCD tail",
            parser.parse("%%gradient:#000000:#ffffff%%AB&cCD%%/gradient%% tail"));
    }

    @Test
    void innerHexColorOverridesTheGradientWithoutRestoration() {
        assertEquals("&#000000A&#555555B&#123456CD",
            parser.parse("%%gradient:#000000:#ffffff%%AB&#123456CD%%/gradient%%"));
    }

    @Test
    void fontCodesPassThroughUnchanged() {
        // The segment lexer carries fonts across color changes, so one pass-through suffices.
        assertEquals("&g&#000000A&#ffffffB&r",
            parser.parse("%%gradient:#000000:#ffffff%%&gAB%%/gradient%%"));
    }

    @Test
    void sectionSymbolCodesAreCanonicalizedToAmpersand() {
        assertEquals("&#000000&lA&#ffffff&lB&r",
            parser.parse("%%gradient:#000000:#ffffff%%§lAB%%/gradient%%"));
    }

    @Test
    void duplicateFormattingCodesAreNotRepeated() {
        assertEquals("&#000000&lA&#ffffff&lB&r",
            parser.parse("%%gradient:#000000:#ffffff%%&l&lAB%%/gradient%%"));
    }

    @Test
    void loneTrailingSymbolIsColoredAsText() {
        // A '&' not followed by a valid code is a visible character.
        assertEquals("&#000000A&#ffffff&&r",
            parser.parse("%%gradient:#000000:#ffffff%%A&%%/gradient%%"));
    }

    @Test
    void gradientResolvesThroughTheFullParseChain() {
        assertEquals("&#000000A&#404040B &#bfbfbfC&#ffffffD&r",
            TextWrapper.parseLine("%%gradient:#000000:#ffffff%%AB CD%%/gradient%%"));
    }

    @Test
    void namedColorPlaceholderInsideTheBodyBecomesAnInnerOverride() {
        // ColorCodeParser runs first, so %%RED%% is already &c when the gradient expands.
        assertEquals("&#000000A&#555555B&cCD",
            TextWrapper.parseLine("%%gradient:#000000:#ffffff%%AB%%RED%%CD%%/gradient%%"));
    }

    @Test
    void hexPlaceholderInsideTheBodyBecomesAnInnerOverride() {
        assertEquals("&#000000A&#555555B&#123456CD",
            TextWrapper.parseLine("%%gradient:#000000:#ffffff%%AB%%#123456%%CD%%/gradient%%"));
    }

    @Test
    void gradientSurvivesLineWrapping() {
        List<String> lines = TextWrapper.wrapString(
            "%%gradient:#ff0000:#0000ff%%AAAA BBBB%%/gradient%%", 6);
        assertEquals(2, lines.size());
        assertEquals("&#ff0000A&#df0020A&#bf0040A&#9f0060A ", lines.get(0));
        assertTrue(lines.get(1).startsWith("&#9f0060"), "wrap carries the last gradient color forward");
        assertTrue(lines.get(1).endsWith("&#0000ffB&r"), "gradient finishes on the wrapped line");
    }
}
