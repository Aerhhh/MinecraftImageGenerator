package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import net.aerh.imagegenerator.exception.PackResolveException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Size values inside a pack that used to bypass {@link PackLimits}: each of these let a few bytes of
 * pack content ask for gigabytes when rendered.
 */
class PackSizeLimitsTest {

    private static byte[] encode(int width, int height, String format) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int type = format.equals("png") ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        assertTrue(ImageIO.write(new BufferedImage(width, height, type), format, out));
        return out.toByteArray();
    }

    private static void parseFont(String provider) {
        PackFontParser.parse(("{\"providers\":[" + provider + "]}").getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void spriteScalingCannotDemandAHugeNormalizedCopy() {
        BufferedImage onePixel = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);

        assertThrows(IllegalArgumentException.class,
            () -> GuiSpriteRenderer.render(onePixel, new GuiScaling.Tile(32_768, 32_768), 16, 16));
        assertThrows(IllegalArgumentException.class, () -> GuiSpriteRenderer.render(onePixel,
            new GuiScaling.NineSlice(32_768, 32_768, new GuiScaling.NineSlice.Border(1, 1, 1, 1), false), 16, 16));
        assertDoesNotThrow(() -> GuiSpriteRenderer.render(onePixel, new GuiScaling.Tile(64, 64), 16, 16));
    }

    @Test
    void bitmapFontHeightIsBounded() {
        String chars = "\"chars\":[\"A\"]";
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"bitmap\",\"file\":\"ns:font/a.png\",\"height\":20000,\"ascent\":0," + chars + "}"));
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"bitmap\",\"file\":\"ns:font/a.png\",\"height\":8,\"ascent\":-100000," + chars + "}"));
        assertDoesNotThrow(
            () -> parseFont("{\"type\":\"bitmap\",\"file\":\"ns:font/a.png\",\"height\":256,\"ascent\":200," + chars + "}"));
    }

    @Test
    void ttfSizeAndOversampleAreBounded() {
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"ttf\",\"file\":\"ns:a.ttf\",\"size\":0.0005,\"oversample\":20000}"));
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"ttf\",\"file\":\"ns:a.ttf\",\"size\":30000}"));
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"ttf\",\"file\":\"ns:a.ttf\",\"size\":200,\"oversample\":16}"));
        assertThrows(PackLoadException.class,
            () -> parseFont("{\"type\":\"ttf\",\"file\":\"ns:a.ttf\",\"size\":0}"));
        assertDoesNotThrow(() -> parseFont("{\"type\":\"ttf\",\"file\":\"ns:a.ttf\",\"size\":7,\"oversample\":4}"));
    }

    @Test
    void texturesMustBePng() throws IOException {
        assertDoesNotThrow(() -> TextureDecoder.decode(encode(16, 16, "png"), 1_024, false));
        // Decodable by ImageIO, and within the size cap, but not PNG
        byte[] bmp = encode(16, 16, "bmp");
        byte[] gif = encode(16, 16, "gif");
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(bmp, 1_024, false));
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(gif, 1_024, false));
    }

    @Test
    void textureAreaIsBoundedUnderTheSheetCap() throws IOException {
        // Within the 8192 per-side sheet cap, but 5000x5000 is 25M pixels
        byte[] square = encode(5_000, 5_000, "png");
        assertThrows(PackLoadException.class, () -> TextureDecoder.decode(square, 8_192, false));
        // A long narrow sheet of the same height still decodes
        assertDoesNotThrow(() -> TextureDecoder.decode(encode(64, 5_000, "png"), 8_192, false));
    }

    @Test
    void defaultFlipbookOrderStopsAtThePlayableFrameCount() {
        // 64x64 cut into 1x1 frames is 4096 cells; only the first MAX_CYCLE_TICKS can ever play
        BufferedImage sheet = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        PackAnimation animation = PackAnimation.resolve(sheet, new AnimationMeta(null, 1, 1, 1, false)).orElseThrow();
        assertEquals(AnimationTimeline.MAX_CYCLE_TICKS, animation.frames().size());
    }

    @Test
    void explicitFrameListStopsAtThePlayableFrameCount() {
        BufferedImage sheet = new BufferedImage(16, 32, BufferedImage.TYPE_INT_ARGB);
        List<AnimationMeta.FrameEntry> entries = new ArrayList<>();
        for (int i = 0; i < 50_000; i++) {
            entries.add(new AnimationMeta.FrameEntry(i % 2, 1));
        }
        PackAnimation animation = PackAnimation.resolve(sheet, new AnimationMeta(entries, 1, null, null, false)).orElseThrow();
        assertEquals(AnimationTimeline.MAX_CYCLE_TICKS, animation.frames().size());
    }

    @Test
    void modelElementCountIsCheckedWhenParsing() {
        String element = "{\"from\":[0,0,0],\"to\":[1,1,1],\"faces\":{}}";
        String many = String.join(",", java.util.Collections.nCopies(ElementModelRenderer.MAX_ELEMENTS_PER_MODEL + 1, element));
        assertThrows(PackLoadException.class,
            () -> PackJsonParser.parseModel(("{\"elements\":[" + many + "]}").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void dotOnlyNamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ResourceRef(".", "item/foo"));
        assertDoesNotThrow(() -> new ResourceRef("my.pack", "item/foo"));
    }

    @Test
    void limitsCarryADefaultIndexBudget() {
        PackLimits fiveArg = new PackLimits(100, 1_024, 64, 1_024, 128);
        assertEquals(256L * 1024 * 1024, fiveArg.maxIndexBytes());
        assertEquals(fiveArg.maxIndexBytes(), PackLimits.fromSystemProperties().maxIndexBytes());
        assertThrows(IllegalArgumentException.class, () -> new PackLimits(100, 1_024, 64, 1_024, 128, 0));
    }

    @Test
    void unknownPackErrorDoesNotListOtherPacks() {
        PackResolveException thrown = assertThrows(PackResolveException.class,
            () -> PackRepository.global().resolve(PackId.parse("nobody:registered-this"), "minecraft:item/stone"));
        assertEquals("Pack `nobody:registered-this` is not registered", thrown.getMessage());
    }
}
