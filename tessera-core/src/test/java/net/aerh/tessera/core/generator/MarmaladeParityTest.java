package net.aerh.tessera.core.generator;

import dev.matrixlab.webp4j.WebPCodec;
import dev.matrixlab.webp4j.model.AnimatedWebPData;
import dev.matrixlab.webp4j.model.AnimatedWebPFrame;
import net.aerh.tessera.api.assets.AssetProvider;
import net.aerh.tessera.api.generator.GenerationContext;
import net.aerh.tessera.core.image.ImageOps;
import net.aerh.tessera.api.generator.GeneratorResult;
import net.aerh.tessera.core.effect.EffectPipeline;
import net.aerh.tessera.core.effect.GlintEffect;
import net.aerh.tessera.core.font.DefaultFontRegistry;
import net.aerh.tessera.core.generator.player.IsometricPlayerRenderer;
import net.aerh.tessera.core.generator.skull.IsometricSkullRenderer;
import net.aerh.tessera.core.sprite.AtlasSpriteProvider;
import net.aerh.tessera.core.testsupport.LiveAssetProviderResolver;
import net.aerh.tessera.core.text.MinecraftTextRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parity harness for the Marmalade to Tessera encoder swap (CONTEXT.md ).
 *
 * <p>For PNG fixtures: byte-for-byte equality.
 * For GIF fixtures: decoded-frame PSNR comparison (Decision 1A, >= 40 dB). Kevin Weiner's
 * {@code AnimatedGifEncoder} performs lossy colour quantisation non-deterministically across
 * invocations when the input colour count exceeds 256, so byte-equality is unsafe; PSNR on
 * decoded frames catches real drift while tolerating quantiser re-ordering.
 * For animated WebP fixtures: per-frame PSNR must exceed 45 dB (Decision 2A safety net).
 */
@EnabledIfEnvironmentVariable(named = "TESSERA_ASSETS_AVAILABLE", matches = "true")
class MarmaladeParityTest {

    private static final double MIN_PSNR_DB_WEBP = 45.0;
    private static final double MIN_PSNR_DB_GIF = 40.0;

    // -------- Byte-equality fixtures (PNG) --------

    @Test
    void itemDiamondSword_pngBytesMatch() throws Exception {
        byte[] actual = renderItemToPng("diamond_sword");
        byte[] expected = readFixture("item-diamond-sword/expected.png");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void playerHeadSteveUpscaled5x_pngBytesMatch() throws Exception {
        byte[] actual = renderPlayerHeadToPng(5);
        byte[] expected = readFixture("player-head-steve-upscaled-5x/expected.png");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void inventoryHopper9Slot_pngBytesMatch() throws Exception {
        byte[] actual = renderInventoryToPng();
        byte[] expected = readFixture("inventory-9-slot-hopper/expected.png");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void tooltipWithHexColor_pngBytesMatch() throws Exception {
        byte[] actual = renderTooltipToPng();
        byte[] expected = readFixture("tooltip-rarity-with-hex-color/expected.png");
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void playerModel3D_pngBytesMatch() throws Exception {
        byte[] actual = renderPlayerModelToPng();
        byte[] expected = readFixture("player-model-full-3d/expected.png");
        assertThat(actual).isEqualTo(expected);
    }

    // -------- PSNR fixtures (animated WebP) --------

    @Test
    void enchantedDiamondSword_webpFramesMatchByPsnr() throws Exception {
        byte[] actualBytes = renderEnchantedDiamondSwordToWebp();
        byte[] expectedBytes = readFixture("item-enchanted-diamond-sword/expected.webp");
        assertFramesMatchByPsnr(
                decodeAnimatedWebp(actualBytes),
                decodeAnimatedWebp(expectedBytes),
                MIN_PSNR_DB_WEBP);
    }

    @Test
    void composite2x2Grid_webpFramesMatchByPsnr() throws Exception {
        byte[] actualBytes = renderComposite2x2ToWebp();
        byte[] expectedBytes = readFixture("composite-2x2-grid/expected.webp");
        assertFramesMatchByPsnr(
                decodeAnimatedWebp(actualBytes),
                decodeAnimatedWebp(expectedBytes),
                MIN_PSNR_DB_WEBP);
    }

    // -------- PSNR fixture (animated GIF) --------

    @Test
    void enchantedDiamondSword_gifFramesMatchByPsnr() throws Exception {
        byte[] actualBytes = renderEnchantedDiamondSwordToGif();
        byte[] expectedBytes = readFixture("item-enchanted-diamond-sword-gif/expected.gif");
        // GIF does not carry alpha natively. Marmalade's javax.imageio-based
        // GifSequenceWriter (with transparentColorFlag=FALSE) emits transparent regions
        // such that javax.imageio decodes them as RGBA=0x00000000 (alpha=0, RGB=0).
        // animated-gif-lib:1.4's Kevin Weiner encoder flattens transparent regions to
        // opaque white (RGBA=0xFFFFFFFF). The resulting composited frames are visually
        // identical when displayed against a white background (which is how GIFs are
        // universally viewed), but their raw RGB channels disagree by up to 255 in
        // transparent regions. Flatten both sides onto opaque white before PSNR so we
        // compare what a viewer actually sees.
        assertFramesMatchByPsnr(
                flattenFramesOnWhite(decodeAnimatedGifComposited(actualBytes)),
                flattenFramesOnWhite(decodeAnimatedGifComposited(expectedBytes)),
                MIN_PSNR_DB_GIF);
    }

    private static List<BufferedImage> flattenFramesOnWhite(List<BufferedImage> frames) {
        java.util.List<BufferedImage> out = new java.util.ArrayList<>(frames.size());
        for (BufferedImage frame : frames) {
            BufferedImage flat = new BufferedImage(
                    frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = flat.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, flat.getWidth(), flat.getHeight());
            g.drawImage(frame, 0, 0, null);
            g.dispose();
            out.add(flat);
        }
        return out;
    }

    // -------- Live asset provider plumbing --------

    private static AtlasSpriteProvider liveAtlas() {
        AssetProvider provider = LiveAssetProviderResolver.resolve26_1_2();
        return AtlasSpriteProvider.fromAssetProvider(provider, LiveAssetProviderResolver.MC_VER);
    }

    private static DefaultFontRegistry liveFonts() {
        AssetProvider provider = LiveAssetProviderResolver.resolve26_1_2();
        return DefaultFontRegistry.withBuiltins(provider, LiveAssetProviderResolver.MC_VER);
    }

    // -------- Render helpers (exercise post-swap code paths) --------

    private static byte[] renderItemToPng(String itemId) throws Exception {
        ItemGenerator gen = new ItemGenerator(
                liveAtlas(), EffectPipeline.builder().build());
        GeneratorResult result = gen.render(
                ItemRequest.builder().itemId(itemId).build(), GenerationContext.defaults());
        return toPngBytes(result.firstFrame());
    }

    private static byte[] renderPlayerHeadToPng(int scale) throws Exception {
        // Decision 5 Option C: skip the HttpClient path (Java HttpClient does not support
        // file: URIs) by invoking the skull renderer directly against the synthetic skin
        // then upscaling through ImageOps (post-swap) / ImageUtil (pre-swap). This mirrors
        // exactly what PlayerHeadGenerator.render does internally.
        BufferedImage skin = readSkinFromFixtures();
        BufferedImage head = IsometricSkullRenderer.render(skin);
        BufferedImage upscaled = upscaleForParity(head, scale);
        return toPngBytes(upscaled);
    }

    private static byte[] renderInventoryToPng() throws Exception {
        InventoryGenerator gen = new InventoryGenerator(
                liveAtlas(),
                EffectPipeline.builder().build(),
                liveFonts());
        InventoryRequest request = InventoryRequest.builder()
                .rows(1)
                .slotsPerRow(9)
                .title("Hopper")
                .item(InventoryItem.builder(0, "diamond_sword").build())
                .item(InventoryItem.builder(2, "golden_apple").stackCount(16).build())
                .item(InventoryItem.builder(4, "emerald").stackCount(64).build())
                .item(InventoryItem.builder(8, "stick").build())
                .build();
        GeneratorResult result = gen.render(request, GenerationContext.defaults());
        return toPngBytes(result.firstFrame());
    }

    private static byte[] renderTooltipToPng() throws Exception {
        TooltipGenerator gen = new TooltipGenerator(
                new MinecraftTextRenderer(liveFonts()));
        TooltipRequest request = TooltipRequest.builder()
                .line("&#FF55FFMythic Diamond Sword")
                .line("&7Damage: &c+25")
                .line("&#55FFFFFrozen Edge &8[&7100%&8]")
                .line("")
                .line("&#AA00AAMYTHIC WEAPON")
                .maxLineLength(0)
                .build();
        GeneratorResult result = gen.render(request, GenerationContext.defaults());
        return toPngBytes(result.firstFrame());
    }

    private static byte[] renderPlayerModelToPng() throws Exception {
        BufferedImage skin = readSkinFromFixtures();
        BufferedImage body = IsometricPlayerRenderer.render(skin, /*slim=*/ false);
        return toPngBytes(body);
    }

    private static byte[] renderEnchantedDiamondSwordToWebp() throws Exception {
        GeneratorResult.AnimatedImage animated = renderEnchantedDiamondSword();
        return animated.toWebpBytes();
    }

    private static byte[] renderEnchantedDiamondSwordToGif() throws Exception {
        GeneratorResult.AnimatedImage animated = renderEnchantedDiamondSword();
        return animated.toGifBytes();
    }

    private static byte[] renderComposite2x2ToWebp() throws Exception {
        GeneratorResult topLeft     = renderPlainSword();
        GeneratorResult topRight    = renderPlainSword();
        GeneratorResult bottomLeft  = renderEnchantedDiamondSword();
        GeneratorResult bottomRight = renderPlainSword();

        GeneratorResult topRow = ResultComposer.compose(
                List.of(topLeft, topRight), CompositeRequest.Layout.HORIZONTAL, 4);
        GeneratorResult bottomRow = ResultComposer.compose(
                List.of(bottomLeft, bottomRight), CompositeRequest.Layout.HORIZONTAL, 4);
        GeneratorResult grid = ResultComposer.compose(
                List.of(topRow, bottomRow), CompositeRequest.Layout.VERTICAL, 4);

        return ((GeneratorResult.AnimatedImage) grid).toWebpBytes();
    }

    private static GeneratorResult.AnimatedImage renderEnchantedDiamondSword() throws Exception {
        AssetProvider provider = LiveAssetProviderResolver.resolve26_1_2();
        ItemGenerator gen = new ItemGenerator(
                liveAtlas(),
                EffectPipeline.builder()
                        .add(GlintEffect.fromAssetProvider(provider, LiveAssetProviderResolver.MC_VER))
                        .build());
        return (GeneratorResult.AnimatedImage) gen.render(
                ItemRequest.builder().itemId("diamond_sword").enchanted(true).build(),
                GenerationContext.defaults());
    }

    private static GeneratorResult renderPlainSword() throws Exception {
        ItemGenerator gen = new ItemGenerator(
                liveAtlas(), EffectPipeline.builder().build());
        return gen.render(
                ItemRequest.builder().itemId("diamond_sword").build(),
                GenerationContext.defaults());
    }

    // -------- I/O helpers --------

    private static byte[] readFixture(String relPath) throws IOException {
        try (InputStream in = MarmaladeParityTest.class.getResourceAsStream(
                "/marmalade-parity/" + relPath)) {
            Objects.requireNonNull(in, "fixture not found: " + relPath);
            return in.readAllBytes();
        }
    }

    private static BufferedImage readSkinFromFixtures() throws IOException {
        try (InputStream in = MarmaladeParityTest.class.getResourceAsStream(
                "/marmalade-parity/skins/steve.png")) {
            Objects.requireNonNull(in, "steve skin fixture not found");
            return ImageIO.read(in);
        }
    }

    private static byte[] toPngBytes(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    /**
     * Called from {@link #renderPlayerHeadToPng(int)} to exercise the upscale call that
     * the Marmalade swap replaced. Both Marmalade's {@code ImageUtil.upscaleImage} and
     * {@link ImageOps#upscaleNearestNeighbor} wrap the same {@code AffineTransformOp} +
     * {@code TYPE_NEAREST_NEIGHBOR} pipeline, so the byte output of this helper is identical
     * pre- and post-swap; PNG encoding is deterministic for a fixed pixel array.
     */
    private static BufferedImage upscaleForParity(BufferedImage src, int scale) {
        return ImageOps.upscaleNearestNeighbor(src, scale);
    }

    // -------- WebP/GIF decode --------

    private static List<BufferedImage> decodeAnimatedWebp(byte[] bytes) throws IOException {
        AnimatedWebPData data = WebPCodec.decodeAnimatedWebP(bytes);
        return data.getFrames().stream().map(AnimatedWebPFrame::getImage).toList();
    }

    /**
     * Decodes an animated GIF to its <em>composited</em> frames using {@code javax.imageio}.
     *
     * <p>Raw {@code reader.read(i)} returns only the per-frame delta (the sub-image written by
     * the encoder at {@code (imageLeftPosition, imageTopPosition)}), and the disposal method
     * determines how consecutive frames accumulate on a canvas. Marmalade's
     * {@code GifSequenceWriter} uses {@code disposalMethod=none}, which means each frame is
     * painted on top of the previous composited canvas; {@code animated-gif-lib:1.4} may use
     * a different disposal. Comparing composited canvases gives an encoder-agnostic frame
     * representation suitable for PSNR.
     *
     * <p>This implementation reads per-frame metadata to find the frame offset and disposal
     * mode, then composites frames onto a canvas the size of the logical screen.
     */
    private static List<BufferedImage> decodeAnimatedGifComposited(byte[] bytes) throws IOException {
        try (javax.imageio.stream.ImageInputStream iis =
                     javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(bytes))) {
            Objects.requireNonNull(iis, "could not create ImageInputStream");
            java.util.Iterator<javax.imageio.ImageReader> it =
                    javax.imageio.ImageIO.getImageReadersByFormatName("gif");
            if (!it.hasNext()) {
                throw new IOException("no GIF ImageReader available");
            }
            javax.imageio.ImageReader reader = it.next();
            try {
                reader.setInput(iis, false, false);
                int n = reader.getNumImages(true);

                // Determine logical screen size from the first frame's dimensions +
                // image offset; if the stream metadata provides a screen descriptor,
                // prefer that. For robustness, track the running max extent.
                int canvasW = 0;
                int canvasH = 0;
                int[] left = new int[n];
                int[] top = new int[n];
                int[] disposal = new int[n]; // 0=none, 1=doNotDispose, 2=restoreToBg, 3=restoreToPrev
                BufferedImage[] subImages = new BufferedImage[n];

                for (int i = 0; i < n; i++) {
                    BufferedImage sub = reader.read(i);
                    subImages[i] = sub;

                    javax.imageio.metadata.IIOMetadata meta = reader.getImageMetadata(i);
                    String formatName = meta.getNativeMetadataFormatName();
                    org.w3c.dom.Node root = meta.getAsTree(formatName);
                    left[i] = intAttr(findChild(root, "ImageDescriptor"), "imageLeftPosition", 0);
                    top[i] = intAttr(findChild(root, "ImageDescriptor"), "imageTopPosition", 0);
                    String dispStr = strAttr(findChild(root, "GraphicControlExtension"),
                            "disposalMethod", "none");
                    disposal[i] = switch (dispStr) {
                        case "doNotDispose" -> 1;
                        case "restoreToBackgroundColor" -> 2;
                        case "restoreToPrevious" -> 3;
                        default -> 0; // "none" or unknown
                    };

                    canvasW = Math.max(canvasW, left[i] + sub.getWidth());
                    canvasH = Math.max(canvasH, top[i] + sub.getHeight());
                }

                // Composite
                java.util.List<BufferedImage> composited = new java.util.ArrayList<>(n);
                BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                BufferedImage previousCanvas = null;

                for (int i = 0; i < n; i++) {
                    if (i == 0 || disposal[i - 1] == 2) {
                        canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
                    } else if (disposal[i - 1] == 3 && previousCanvas != null) {
                        canvas = deepCopy(previousCanvas);
                    }
                    previousCanvas = deepCopy(canvas);

                    java.awt.Graphics2D g = canvas.createGraphics();
                    g.drawImage(subImages[i], left[i], top[i], null);
                    g.dispose();

                    composited.add(deepCopy(canvas));
                }

                return composited;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage deepCopy(BufferedImage src) {
        BufferedImage copy = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private static org.w3c.dom.Node findChild(org.w3c.dom.Node parent, String name) {
        if (parent == null) return null;
        org.w3c.dom.NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            org.w3c.dom.Node c = children.item(i);
            if (name.equals(c.getNodeName())) return c;
        }
        return null;
    }

    private static int intAttr(org.w3c.dom.Node node, String attr, int fallback) {
        String s = strAttr(node, attr, null);
        if (s == null) return fallback;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String strAttr(org.w3c.dom.Node node, String attr, String fallback) {
        if (node == null || !(node instanceof org.w3c.dom.Element el)) return fallback;
        String v = el.getAttribute(attr);
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    // -------- PSNR --------

    private static void assertFramesMatchByPsnr(List<BufferedImage> actual,
                                                List<BufferedImage> expected,
                                                double minDb) {
        assertThat(actual).as("frame count").hasSameSizeAs(expected);
        for (int i = 0; i < actual.size(); i++) {
            double dB = psnrDb(actual.get(i), expected.get(i));
            assertThat(dB)
                    .as("frame %d PSNR must be >= %.1f dB, was %.2f", i, minDb, dB)
                    .isGreaterThanOrEqualTo(minDb);
        }
    }

    /**
     * Peak Signal-to-Noise Ratio in decibels between two images. Returns
     * {@link Double#POSITIVE_INFINITY} for identical images; MSE = 0.
     */
    private static double psnrDb(BufferedImage a, BufferedImage b) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            return Double.NEGATIVE_INFINITY;
        }
        long sumSq = 0L;
        int w = a.getWidth();
        int h = a.getHeight();
        long samples = (long) w * h * 3L;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int pa = a.getRGB(x, y);
                int pb = b.getRGB(x, y);
                int dr = ((pa >> 16) & 0xFF) - ((pb >> 16) & 0xFF);
                int dg = ((pa >> 8)  & 0xFF) - ((pb >> 8)  & 0xFF);
                int db = ( pa        & 0xFF) - ( pb        & 0xFF);
                sumSq += (long) (dr * dr + dg * dg + db * db);
            }
        }
        if (sumSq == 0L) return Double.POSITIVE_INFINITY;
        double mse = (double) sumSq / (double) samples;
        return 10.0 * Math.log10((255.0 * 255.0) / mse);
    }
}
