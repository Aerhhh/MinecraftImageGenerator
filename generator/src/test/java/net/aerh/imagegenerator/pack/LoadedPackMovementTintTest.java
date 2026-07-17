package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.pack.font.PackFont;
import net.aerh.imagegenerator.pack.font.PackGlyph;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end gating of the movement shader no-tint rule through pack loading: a pack that ships the
 * shader chain keeps its native glyph art under a movement marker run color, while the SAME art in a
 * pack without the shader tints vanilla-style.
 */
class LoadedPackMovementTintTest {

    private static final int NATIVE_TEAL = 0xFF159485;

    @TempDir
    Path packDir;

    @BeforeEach
    void writePack() {
        FixturePacks.writeMovementShaderFontPack(packDir);
    }

    private LoadedPack load() {
        return new LoadedPack(PackId.parse("test:mv"),
            PackSource.directory(packDir, PackLimits.fromSystemProperties()),
            PackLimits.fromSystemProperties());
    }

    /** Draws U+E000 of {@code testpack:art} at pixelSize 1: its native teal texel lands at (0,5). */
    private static int drawArtTopLeft(LoadedPack pack, Color runColor) {
        PackFont font = pack.resolveFont("testpack:art").orElseThrow();
        PackGlyph glyph = font.glyph(0xE000).orElseThrow();
        BufferedImage canvas = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = canvas.createGraphics();
        try {
            glyph.draw(graphics, 0.0, 0.0, 1, runColor, false);
        } finally {
            graphics.dispose();
        }
        return canvas.getRGB(0, 5);
    }

    @Test
    void shaderPackKeepsNativeArtUnderMarkerRunColor() {
        assertEquals(NATIVE_TEAL, drawArtTopLeft(load(), new Color(0, 235, 28)),
            "the shipped movement shader neutralizes the marker run color, keeping native art");
    }

    @Test
    void shaderPackStillTintsOrdinaryRunColors() {
        // A non-marker run color (green 200 is not the marker) tints normally even with the shader.
        // teal 0x159485 * (200,200,200)/255, each channel rounded to nearest.
        assertEquals(0xFF107468, drawArtTopLeft(load(), new Color(200, 200, 200)),
            "ordinary text still tints under the shader");
    }

    @Test
    void withoutShaderTheSameArtTintsVanillaStyle() throws IOException {
        Files.delete(packDir.resolve("assets/minecraft/shaders/include/config/movement.glsl"));
        assertEquals(0xFF00880F, drawArtTopLeft(load(), new Color(0, 235, 28)),
            "no shader chain: the marker run color tints the native teal green, destroying its red");
    }
}
