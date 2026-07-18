package net.aerh.imagegenerator.pack.font;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The movement marker match and tint substitution, matched on the raw run color's green and blue
 * channels exactly like the shader's {@code isMovement} test.
 */
class MovementTintRuleTest {

    private static final MovementTintRule RULE = new MovementTintRule(235, Set.of(0, 4, 28, 72));

    @Test
    void matchesMarkerGreenAndBlueRegardlessOfRedAndAlpha() {
        assertTrue(RULE.neutralizesTint(new Color(0, 235, 28)));
        assertTrue(RULE.neutralizesTint(new Color(200, 235, 28, 40)), "red and alpha are ignored by the match");
        assertTrue(RULE.neutralizesTint(new Color(0, 235, 0)), "blue 0 is a defined marker");
        assertTrue(RULE.neutralizesTint(new Color(0, 235, 72)), "blue 72 is a defined marker");
    }

    @Test
    void rejectsNonMarkerRuns() {
        assertFalse(RULE.neutralizesTint(new Color(0, 235, 30)), "blue 30 is not in the table");
        assertFalse(RULE.neutralizesTint(new Color(0, 234, 28)), "green 234 is not the marker");
        assertFalse(RULE.neutralizesTint(Color.WHITE));
        assertFalse(RULE.neutralizesTint(null), "a null run color never matches");
    }

    @Test
    void effectiveTintNeutralizesMarkerToWhiteKeepingAlpha() {
        assertEquals(new Color(255, 255, 255, 255), RULE.effectiveTint(new Color(0, 235, 28)),
            "opaque marker becomes opaque white so the multiply keeps the native texel");
        assertEquals(new Color(255, 255, 255, 128), RULE.effectiveTint(new Color(17, 235, 4, 128)),
            "translucent marker keeps its alpha so the texel alpha still scales");
    }

    @Test
    void effectiveTintPassesNonMarkerRunsThroughUnchanged() {
        Color ordinary = new Color(128, 64, 32);
        assertSame(ordinary, RULE.effectiveTint(ordinary), "ordinary text tints exactly as before");
    }
}
