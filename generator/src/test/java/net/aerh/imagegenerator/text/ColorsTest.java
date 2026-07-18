package net.aerh.imagegenerator.text;

import lib.minecraft.text.ChatColor;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Covers MIG's in-band hex parsing and channel interpolation ({@link Colors}), the pieces of the
 * retired {@code RgbColor} record that had no upstream equivalent. The named-color, shadow, and
 * JSON-parsing behaviour now lives in the {@code lib.minecraft.text.ChatColor} model and is tested
 * there; the one shadow assertion here pins the 1.13+ formula MIG relies on (decision 2B).
 */
class ColorsTest {

    @Test
    void parsesHashPrefixedSixDigitHex() {
        assertEquals(ChatColor.of(0xFF00AA), Colors.tryParseHex("#FF00AA"));
    }

    @Test
    void parsingIsCaseInsensitive() {
        assertEquals(ChatColor.of(0xABCDEF), Colors.tryParseHex("#abCDef"));
    }

    @Test
    void rejectsMalformedHexStrings() {
        assertNull(Colors.tryParseHex("FF00AA"), "missing hash prefix");
        assertNull(Colors.tryParseHex("#FFF"), "shorthand not supported");
        assertNull(Colors.tryParseHex("#GGGGGG"), "non-hex digits");
        assertNull(Colors.tryParseHex("#FF00AA0"), "too many digits");
        assertNull(Colors.tryParseHex("#"), "no digits");
        assertNull(Colors.tryParseHex(""), "empty string");
    }

    @Test
    void parsesInBandHexAtOffset() {
        // The '#' sits at index 1 after a '&'/'§' symbol; trailing text is ignored.
        assertEquals(ChatColor.of(0xFF00AA), Colors.tryParseHexAt("&#ff00aatail", 1));
        assertNull(Colors.tryParseHexAt("&#ff00a", 1), "five hex digits is not a color");
        assertNull(Colors.tryParseHexAt("&xff00aa", 1), "must start with '#'");
    }

    @Test
    void derivesVanillaShadowForCustomColors() {
        // Decision 2B: MIG relies on the library's 1.13+ shadow formula (0xFF00AA & 0xFCFCFC) >> 2.
        assertEquals(new Color(0x3F002A), ChatColor.of(0xFF00AA).backgroundColor());
    }

    @Test
    void lerpEndpointsReturnTheStops() {
        assertEquals(0x123456, Colors.lerp(0x123456, 0xABCDEF, 0.0));
        assertEquals(0xABCDEF, Colors.lerp(0x123456, 0xABCDEF, 1.0));
    }

    @Test
    void lerpRoundsHalfUpPerChannel() {
        // Birdflop vector: t = 0.25 of black -> white gives 63.75, which rounds to 0x40.
        assertEquals(0x404040, Colors.lerp(0x000000, 0xFFFFFF, 0.25));
        // Birdflop vector: midpoint of black -> white is 127.5, which rounds up to 0x80.
        assertEquals(0x808080, Colors.lerp(0x000000, 0xFFFFFF, 0.5));
        // Birdflop vector: midpoint of #ffffff -> #599fb0; blue channel 215.5 rounds up to 0xD8.
        assertEquals(0xACCFD8, Colors.lerp(0xFFFFFF, 0x599FB0, 0.5));
    }

    @Test
    void lerpDescendingChannelRoundsHalfUpToo() {
        // Birdflop vector: t = 0.75 of black -> white gives 191.25 per channel, rounding to 0xBF.
        assertEquals(0xBFBFBF, Colors.lerp(0x000000, 0xFFFFFF, 0.75));
    }
}
