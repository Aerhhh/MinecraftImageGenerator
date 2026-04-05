package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Produces an animated enchantment-glint overlay.
 * <p>
 * The glint is rendered as a 182-frame animation (30 FPS over 6 seconds, each frame 33ms).
 * Two rotation passes are blended per frame - a primary pass rotating at -50 degrees and a
 * secondary pass at +10 degrees. Both passes use bilinear sampling with UV wrapping.
 * <p>
 * The glint texture is loaded from {@code minecraft/assets/textures/glint.png} on the classpath.
 */
public final class GlintEffect implements ImageEffect {

    private static final String ID = "glint";
    private static final int PRIORITY = 100;

    static final int FRAME_COUNT = 182;
    static final int FRAME_DELAY_MS = 33;

    // Dual rotation angles in degrees
    private static final float PRIMARY_ANGLE_DEG = -50f;
    private static final float SECONDARY_ANGLE_DEG = 10f;

    // Scroll periods in milliseconds
    private static final float PRIMARY_PERIOD_MS = 3000f;
    private static final float SECONDARY_PERIOD_MS = 4875f;

    // UV scale applied to the glint texture (higher = more repetitions)
    private static final float UV_SCALE = 8f;

    // Glint tint: purple-ish
    private static final float TINT_R = 0.5f;
    private static final float TINT_G = 0.25f;
    private static final float TINT_B = 0.8f;
    private static final float INTENSITY = 0.75f;

    private static final String GLINT_TEXTURE_PATH = "minecraft/assets/textures/glint.png";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int priority() {
        return PRIORITY;
    }

    @Override
    public boolean appliesTo(EffectContext context) {
        return context.enchanted();
    }

    @Override
    public EffectContext apply(EffectContext context) {
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage base = context.image();
        int w = base.getWidth();
        int h = base.getHeight();

        BufferedImage glintTexture = loadGlintTexture();

        List<BufferedImage> frames = new ArrayList<>(FRAME_COUNT);
        float totalDurationMs = FRAME_COUNT * FRAME_DELAY_MS;

        for (int frameIndex = 0; frameIndex < FRAME_COUNT; frameIndex++) {
            float t = (frameIndex * FRAME_DELAY_MS) / totalDurationMs; // 0..1

            // Primary scroll offset (normalized 0..1)
            float primaryOffset = (t * PRIMARY_PERIOD_MS / totalDurationMs) % 1f;
            // Secondary scroll offset
            float secondaryOffset = (t * SECONDARY_PERIOD_MS / totalDurationMs) % 1f;

            BufferedImage frame = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int basePixel = base.getRGB(x, y);
                    int baseAlpha = (basePixel >> 24) & 0xFF;
                    if (baseAlpha == 0) {
                        // Preserve full transparency
                        frame.setRGB(x, y, 0);
                        continue;
                    }

                    // Normalized pixel coordinates centered at (0.5, 0.5)
                    float nx = (x + 0.5f) / w;
                    float ny = (y + 0.5f) / h;

                    // Primary pass
                    float g1 = sampleRotated(glintTexture, nx, ny, PRIMARY_ANGLE_DEG, primaryOffset);
                    // Secondary pass
                    float g2 = sampleRotated(glintTexture, nx, ny, SECONDARY_ANGLE_DEG, secondaryOffset);

                    float glintBrightness = Math.min(1f, g1 + g2) * INTENSITY;

                    int bR = (basePixel >> 16) & 0xFF;
                    int bG = (basePixel >> 8) & 0xFF;
                    int bB = basePixel & 0xFF;

                    int gR = clamp(Math.round(bR + glintBrightness * TINT_R * 255f));
                    int gG = clamp(Math.round(bG + glintBrightness * TINT_G * 255f));
                    int gB = clamp(Math.round(bB + glintBrightness * TINT_B * 255f));

                    frame.setRGB(x, y, (baseAlpha << 24) | (gR << 16) | (gG << 8) | gB);
                }
            }

            frames.add(frame);
        }

        return context.withAnimationFrames(frames)
                .toBuilder()
                .frameDelayMs(FRAME_DELAY_MS)
                .build();
    }

    /**
     * Samples the glint texture at a rotated and scrolled UV coordinate using bilinear
     * interpolation with wrapping.
     *
     * @param glint     the glint texture
     * @param nx        normalized x [0..1]
     * @param ny        normalized y [0..1]
     * @param angleDeg  rotation angle in degrees
     * @param offset    scroll offset [0..1] along the rotation axis
     * @return normalized brightness [0..1] of the glint at this position
     */
    private static float sampleRotated(BufferedImage glint, float nx, float ny, float angleDeg, float offset) {
        double rad = Math.toRadians(angleDeg);
        float cosA = (float) Math.cos(rad);
        float sinA = (float) Math.sin(rad);

        // Rotate around center (0.5, 0.5)
        float cx = nx - 0.5f;
        float cy = ny - 0.5f;
        float ru = cosA * cx - sinA * cy + 0.5f;
        float rv = sinA * cx + cosA * cy + 0.5f;

        // Scale up and apply scroll offset along u axis
        float u = (ru * UV_SCALE + offset) % 1f;
        float v = (rv * UV_SCALE) % 1f;

        // Wrap negative values
        if (u < 0) u += 1f;
        if (v < 0) v += 1f;

        return bilinearSample(glint, u, v);
    }

    /**
     * Bilinear sample of a texture at UV [0..1] with wrapping.
     *
     * @return normalized brightness (average of RGB channels) [0..1]
     */
    private static float bilinearSample(BufferedImage img, float u, float v) {
        int tw = img.getWidth();
        int th = img.getHeight();

        float px = u * tw - 0.5f;
        float py = v * th - 0.5f;

        int x0 = Math.floorMod((int) Math.floor(px), tw);
        int y0 = Math.floorMod((int) Math.floor(py), th);
        int x1 = Math.floorMod(x0 + 1, tw);
        int y1 = Math.floorMod(y0 + 1, th);

        float fx = px - (float) Math.floor(px);
        float fy = py - (float) Math.floor(py);

        float c00 = brightness(img.getRGB(x0, y0));
        float c10 = brightness(img.getRGB(x1, y0));
        float c01 = brightness(img.getRGB(x0, y1));
        float c11 = brightness(img.getRGB(x1, y1));

        float top = lerp(c00, c10, fx);
        float bot = lerp(c01, c11, fx);
        return lerp(top, bot, fy);
    }

    private static float brightness(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r + g + b) / (3f * 255f);
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static BufferedImage loadGlintTexture() {
        try (InputStream in = GlintEffect.class.getClassLoader().getResourceAsStream(GLINT_TEXTURE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("Glint texture not found on classpath: " + GLINT_TEXTURE_PATH);
            }
            return ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load glint texture: " + GLINT_TEXTURE_PATH, e);
        }
    }
}
