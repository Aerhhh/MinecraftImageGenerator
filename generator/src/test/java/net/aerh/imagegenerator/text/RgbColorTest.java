package net.aerh.imagegenerator.text;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Covers the arbitrary-RGB text color introduced for hex color support: construction,
 * strict parsing, vanilla shadow quartering, legacy/JSON string forms, and the value
 * semantics the reflective render cache key depends on.
 */
class RgbColorTest {

    @Test
    void rejectsOutOfRangeRgb() {
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(-1));
        assertThrows(IllegalArgumentException.class, () -> new RgbColor(0x1000000));
    }

    @Test
    void exposesForegroundColor() {
        assertEquals(new Color(0xFF00AA), new RgbColor(0xFF00AA).getColor());
    }

    @Test
    void derivesShadowByVanillaQuartering() {
        // (0xFF00AA >> 2) & 0x3F3F3F = 0x3F002A, the exact vanilla shadow derivation.
        assertEquals(new Color(0x3F002A), new RgbColor(0xFF00AA).getBackgroundColor());
    }

    @Test
    void parsesHashPrefixedSixDigitHex() {
        assertEquals(new RgbColor(0xFF00AA), RgbColor.tryParse("#FF00AA"));
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(new RgbColor(0xABCDEF), RgbColor.tryParse("#abCDef"));
    }

    @Test
    void rejectsMalformedHexStrings() {
        assertNull(RgbColor.tryParse("FF00AA"), "missing hash prefix");
        assertNull(RgbColor.tryParse("#FFF"), "shorthand not supported");
        assertNull(RgbColor.tryParse("#GGGGGG"), "non-hex digits");
        assertNull(RgbColor.tryParse("#FF00AA0"), "too many digits");
        assertNull(RgbColor.tryParse("#"), "no digits");
        assertNull(RgbColor.tryParse(""), "empty string");
    }

    @Test
    void emitsCanonicalLegacyAndJsonForms() {
        RgbColor color = new RgbColor(0xFF00AA);
        assertEquals("§#ff00aa", color.toLegacyString());
        assertEquals("#ff00aa", color.toJsonString());
        assertEquals("#ff00aa", color.getLegacyCode());
    }

    @Test
    void hasValueSemanticsForCacheKeys() {
        assertEquals(new RgbColor(0xFF00AA), new RgbColor(0xFF00AA));
        assertEquals(new RgbColor(0xFF00AA).hashCode(), new RgbColor(0xFF00AA).hashCode());
        assertEquals("#ff00aa", new RgbColor(0xFF00AA).toString(),
            "toString enters the reflective render cache key and must be stable");
    }

    @Test
    void chatFormatIsATextColor() {
        assertInstanceOf(TextColor.class, ChatFormat.RED);
        assertEquals("c", ChatFormat.RED.getLegacyCode());
        assertEquals(new Color(0xFF5555), ChatFormat.RED.getColor());
    }

    @Test
    void fromJsonStringResolvesNamedColorsToChatFormats() {
        assertSame(ChatFormat.GOLD, TextColor.fromJsonString("gold"));
        assertSame(ChatFormat.DARK_RED, TextColor.fromJsonString("dark_red"));
    }

    @Test
    void fromJsonStringResolvesHexToRgbColor() {
        assertEquals(new RgbColor(0xFF00AA), TextColor.fromJsonString("#ff00aa"));
    }

    @Test
    void fromJsonStringReturnsNullForUnknownValues() {
        assertNull(TextColor.fromJsonString("bogus"));
        assertNull(TextColor.fromJsonString("#ZZZZZZ"));
        assertNull(TextColor.fromJsonString("bold"), "formats are not colors");
    }

    @Test
    void lerpEndpointsReturnTheStops() {
        RgbColor from = new RgbColor(0x123456);
        RgbColor to = new RgbColor(0xABCDEF);
        assertEquals(from, RgbColor.lerp(from, to, 0.0));
        assertEquals(to, RgbColor.lerp(from, to, 1.0));
    }

    @Test
    void lerpRoundsHalfUpPerChannel() {
        // Birdflop vector: t = 0.25 of black -> white gives 63.75, which rounds to 0x40.
        assertEquals(new RgbColor(0x404040),
            RgbColor.lerp(new RgbColor(0x000000), new RgbColor(0xFFFFFF), 0.25));
        // Birdflop vector: midpoint of black -> white is 127.5, which rounds up to 0x80.
        assertEquals(new RgbColor(0x808080),
            RgbColor.lerp(new RgbColor(0x000000), new RgbColor(0xFFFFFF), 0.5));
        // Birdflop vector: midpoint of #ffffff -> #599fb0; blue channel 215.5 rounds up to 0xD8.
        assertEquals(new RgbColor(0xACCFD8),
            RgbColor.lerp(new RgbColor(0xFFFFFF), new RgbColor(0x599FB0), 0.5));
    }

    @Test
    void lerpDescendingChannelRoundsHalfUpToo() {
        // Birdflop vector: t = 0.75 of black -> white gives 191.25 per channel, rounding to 0xBF.
        assertEquals(new RgbColor(0xBFBFBF),
            RgbColor.lerp(new RgbColor(0x000000), new RgbColor(0xFFFFFF), 0.75));
    }
}
