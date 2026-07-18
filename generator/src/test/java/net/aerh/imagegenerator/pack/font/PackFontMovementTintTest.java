package net.aerh.imagegenerator.pack.font;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * The movement shader no-tint emulation as applied through a resolved {@link PackFont}: a movement
 * marker run color keeps a glyph's native texel color while still scaling its alpha, an ordinary run
 * color still tints, and a font built WITHOUT the rule renders byte-identically (the gate).
 */
class PackFontMovementTintTest {

    private static final int TEAL = 0xFF159485;
    private static final int WHITE = 0xFFFFFFFF;

    /** The full config blue table: {@code 0, 4, ..., 72}. */
    private static Set<Integer> markerBlues() {
        Set<Integer> blues = new HashSet<>();
        for (int blue = 0; blue <= 72; blue += 4) {
            blues.add(blue);
        }
        return blues;
    }

    private static final MovementTintRule RULE = new MovementTintRule(235, markerBlues());

    /**
     * 2x2 sheet, chars ["A"]: one 2x2 cell, height 2, ascent 2, scale 1. Native teal at cell (0,0)
     * and white at cell (1,1); the other two texels are transparent. Drawn at pixelSize 1 the cell
     * top sits at {@code 7 - 2 = 5} GUI px, so teal lands at canvas (0,5) and white at (1,6).
     */
    private static PackGlyph glyph(MovementTintRule rule) {
        BufferedImage sheet = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        sheet.setRGB(0, 0, TEAL);
        sheet.setRGB(1, 1, WHITE);
        PackFont font = PackFont.create("test:mv",
            List.of(new FontProviderDefinition.Bitmap("test:font/mv.png", 2, 2, List.of("A"), FontFilter.none())),
            ref -> sheet, new BitmapProviderCache(), rule);
        return font.glyph('A').orElseThrow();
    }

    private static BufferedImage draw(PackGlyph glyph, Color runColor) {
        BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            glyph.draw(graphics, 0.0, 0.0, 1, runColor, false);
        } finally {
            graphics.dispose();
        }
        return canvas;
    }

    @Test
    void markerRunColorKeepsNativeTexelColor() {
        BufferedImage canvas = draw(glyph(RULE), new Color(0, 235, 28));
        assertEquals(TEAL, canvas.getRGB(0, 5), "movement marker run: native teal survives the tint multiply");
        assertEquals(WHITE, canvas.getRGB(1, 6), "movement marker run: native white survives too");
    }

    @Test
    void nonMarkerRunColorStillTints() {
        BufferedImage canvas = draw(glyph(RULE), new Color(128, 64, 32));
        // teal 0x159485 * (128,64,32)/255, each channel rounded to nearest.
        assertEquals(0xFF0B2511, canvas.getRGB(0, 5), "an ordinary run color still multiplies the texel");
        assertEquals(0xFF804020, canvas.getRGB(1, 6), "the white texel becomes the tint color exactly");
    }

    @Test
    void markerRunColorStillScalesOutputAlpha() {
        BufferedImage canvas = draw(glyph(RULE), new Color(0, 235, 28, 128));
        // Texel alpha 255 scaled by run alpha 128: round(255 * 128 / 255) = 128. The white texel
        // stays exact (0x80FFFFFF) - native RGB kept AND the alpha halved. The teal texel's RGB is
        // asserted through its alpha byte only: Java2D's SrcOver blit premultiplies a translucent
        // source and rounds the color channels back, so an exact RGB compare there is a compositing
        // artifact, not the tint math.
        assertEquals(0x80FFFFFF, canvas.getRGB(1, 6), "native white kept, texel alpha scaled to 128");
        assertEquals(128, canvas.getRGB(0, 5) >>> 24, "the native teal texel's alpha is scaled to 128");
    }

    @Test
    void withoutRuleAMarkerRunColorTintsVanillaStyle() {
        BufferedImage canvas = draw(glyph(null), new Color(0, 235, 28));
        // Gate off: the glyph is the raw BitmapGlyph and the marker run color tints like vanilla,
        // forcing the teal texel's red to 0 - the proof the emulation is inactive.
        assertInstanceOf(BitmapGlyph.class, glyph(null), "no rule leaves the concrete glyph un-wrapped");
        assertEquals(0xFF00880F, canvas.getRGB(0, 5), "teal is tinted green, its native red destroyed");
        assertEquals(0xFF00EB1C, canvas.getRGB(1, 6), "the white texel becomes the raw run color");
        assertNotEquals(TEAL, canvas.getRGB(0, 5));
    }
}
