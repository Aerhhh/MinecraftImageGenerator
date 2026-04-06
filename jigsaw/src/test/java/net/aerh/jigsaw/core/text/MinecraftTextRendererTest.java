package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.ChatColor;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MinecraftTextRendererTest {

    private static TextSegment seg(String text) {
        return new TextSegment(text, TextStyle.DEFAULT);
    }

    private static TextSegment colored(String text, Color color) {
        return new TextSegment(text, TextStyle.DEFAULT.withColor(color));
    }

    private static TextLayout oneLineLayout(String text) {
        TextLine line = new TextLine(List.of(seg(text)), text.length());
        return new TextLayout(List.of(line), text.length(), 1);
    }

    // --- basic rendering ---

    @Test
    void renderLayout_emptyLayout_producesMinimalImage() {
        TextLayout empty = new TextLayout(List.of(), 0, 0);
        TextRenderOptions opts = TextRenderOptions.defaults();

        GeneratorResult result = MinecraftTextRenderer.renderLayout(empty, opts);

        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
        BufferedImage image = result.firstFrame();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    @Test
    void renderLayout_returnsStaticImage() {
        TextLayout layout = oneLineLayout("Hello");
        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, TextRenderOptions.defaults());

        assertThat(result.isAnimated()).isFalse();
        assertThat(result).isInstanceOf(GeneratorResult.StaticImage.class);
    }

    @Test
    void renderLayout_nonEmpty_producesNonZeroImage() {
        TextLayout layout = oneLineLayout("Hello");
        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, TextRenderOptions.defaults());

        BufferedImage image = result.firstFrame();
        assertThat(image.getWidth()).isGreaterThan(0);
        assertThat(image.getHeight()).isGreaterThan(0);
    }

    // --- scale factor ---

    @Test
    void renderLayout_higherScaleFactor_producesLargerImage() {
        TextLayout layout = oneLineLayout("Hi");
        TextRenderOptions opts1 = new TextRenderOptions(false, false, false, 1, 255, 7, 13, 200);
        TextRenderOptions opts2 = new TextRenderOptions(false, false, false, 2, 255, 7, 13, 200);

        BufferedImage img1 = MinecraftTextRenderer.renderLayout(layout, opts1).firstFrame();
        BufferedImage img2 = MinecraftTextRenderer.renderLayout(layout, opts2).firstFrame();

        assertThat(img2.getWidth()).isGreaterThan(img1.getWidth());
        assertThat(img2.getHeight()).isGreaterThan(img1.getHeight());
    }

    // --- multiple lines ---

    @Test
    void renderLayout_multipleLines_imageIsTallerThanSingleLine() {
        TextLine line1 = new TextLine(List.of(seg("Line 1")), 6);
        TextLine line2 = new TextLine(List.of(seg("Line 2")), 6);
        TextLayout twoLines = new TextLayout(List.of(line1, line2), 6, 2);

        TextLayout oneLine = oneLineLayout("Line 1");
        TextRenderOptions opts = new TextRenderOptions(false, false, false, 1, 255, 0, 0, 200);

        int h1 = MinecraftTextRenderer.renderLayout(oneLine, opts).firstFrame().getHeight();
        int h2 = MinecraftTextRenderer.renderLayout(twoLines, opts).firstFrame().getHeight();

        assertThat(h2).isGreaterThan(h1);
    }

    // --- image type ---

    @Test
    void renderLayout_imageType_isArgb() {
        TextLayout layout = oneLineLayout("Test");
        BufferedImage image = MinecraftTextRenderer.renderLayout(layout, TextRenderOptions.defaults()).firstFrame();

        assertThat(image.getType()).isEqualTo(BufferedImage.TYPE_INT_ARGB);
    }

    // --- border option ---

    @Test
    void renderLayout_withBorder_doesNotThrow() {
        TextLayout layout = oneLineLayout("Bordered");
        TextRenderOptions opts = new TextRenderOptions(false, true, false, 1, 255, 7, 13, 200);

        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, opts);
        assertThat(result).isNotNull();
    }

    // --- shadow option ---

    @Test
    void renderLayout_withShadow_doesNotThrow() {
        TextLayout layout = oneLineLayout("Shadow");
        TextRenderOptions opts = new TextRenderOptions(true, false, false, 1, 255, 7, 13, 200);

        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, opts);
        assertThat(result).isNotNull();
    }

    // --- colored segment ---

    @Test
    void renderLayout_coloredSegment_doesNotThrow() {
        TextSegment red = colored("Red text", ChatColor.RED.color());
        TextLine line = new TextLine(List.of(red), 8);
        TextLayout layout = new TextLayout(List.of(line), 8, 1);

        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, TextRenderOptions.defaults());
        assertThat(result).isNotNull();
    }

    // --- alpha ---

    @Test
    void renderLayout_zeroAlpha_doesNotThrow() {
        TextLayout layout = oneLineLayout("Invisible");
        TextRenderOptions opts = new TextRenderOptions(false, false, false, 1, 0, 7, 13, 200);

        GeneratorResult result = MinecraftTextRenderer.renderLayout(layout, opts);
        assertThat(result).isNotNull();
    }
}
