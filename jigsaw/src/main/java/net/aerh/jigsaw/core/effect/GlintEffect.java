package net.aerh.jigsaw.core.effect;

import net.aerh.jigsaw.api.effect.EffectContext;
import net.aerh.jigsaw.api.effect.ImageEffect;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Produces an animated enchantment-glint overlay.
 * <p>
 * The glint is rendered as a frame animation (30 FPS over 6 seconds, each frame 33ms).
 * Two rotation passes are blended per frame - a primary pass rotating at -50 degrees and a
 * secondary pass at +10 degrees. Both passes use full 4-channel bilinear sampling with UV wrapping.
 * <p>
 * The glint texture is loaded once from {@code minecraft/assets/textures/glint.png} on the classpath.
 */
public final class GlintEffect implements ImageEffect {

    private static final String ID = "glint";
    private static final int PRIORITY = 100;

    static final int FRAME_DELAY_MS = 33; // ~30 FPS
    private static final int TOTAL_DURATION_MS = 6000;
    private static final double UV_SCALE = 8.0;
    private static final double PRIMARY_ROTATION_DEG = -50.0;
    private static final double SECONDARY_ROTATION_DEG = 10.0;
    private static final int PRIMARY_PERIOD_MS = 3000;
    private static final int SECONDARY_PERIOD_MS = 4875;
    private static final float[] GLINT_TINT = {0.5f, 0.25f, 0.8f};
    private static final float GLINT_INTENSITY = 0.75f;
    private static final double SCROLL_SPEED = 0.3;
    private static final double BASE_SPRITE_PIXELS = 16.0;

    private static final String GLINT_TEXTURE_PATH = "minecraft/assets/textures/glint.png";

    private final BufferedImage glintTexture;

    /**
     * Creates the glint effect, loading the glint texture from the classpath.
     */
    public GlintEffect() {
        this.glintTexture = loadGlintTexture();
    }

    /**
     * Creates the glint effect with the given glint texture.
     *
     * @param glintTexture the glint texture image; must not be {@code null}
     */
    public GlintEffect(BufferedImage glintTexture) {
        Objects.requireNonNull(glintTexture, "glintTexture must not be null");
        this.glintTexture = glintTexture;
    }

    /**
     * Returns the unique identifier for this effect.
     *
     * @return {@code "glint"}
     */
    @Override
    public String id() {
        return ID;
    }

    /**
     * Returns the priority of this effect. Lower values are applied first.
     *
     * @return {@code 100}
     */
    @Override
    public int priority() {
        return PRIORITY;
    }

    /**
     * Returns {@code true} if the item is enchanted (i.e. the glint should be rendered).
     *
     * @param context the current effect context
     * @return whether the enchantment glint applies
     */
    @Override
    public boolean appliesTo(EffectContext context) {
        return context.enchanted();
    }

    /**
     * Generates the animated glint frames and returns the updated context.
     *
     * @param context the current effect context; must not be {@code null}
     * @return the updated context containing all animation frames
     */
    @Override
    public EffectContext apply(EffectContext context) {
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage baseImage = ensureArgb(context.image());

        int frameCount = (int) Math.ceil((double) TOTAL_DURATION_MS / FRAME_DELAY_MS);
        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        int[] basePixels = baseImage.getRGB(0, 0, width, height, null, 0, width);

        double spriteSpanU = BASE_SPRITE_PIXELS / glintTexture.getWidth();
        double spriteSpanV = BASE_SPRITE_PIXELS / glintTexture.getHeight();
        double resolutionScale = Math.max(Math.max(width, height) / BASE_SPRITE_PIXELS, 1.0);
        double uvScale = UV_SCALE / resolutionScale;

        List<BufferedImage> frames = new ArrayList<>(frameCount);

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            double timeMs = frameIndex * FRAME_DELAY_MS;
            int[] framePixels = Arrays.copyOf(basePixels, basePixels.length);

            applyGlintPass(framePixels, width, height, timeMs, PRIMARY_PERIOD_MS, PRIMARY_ROTATION_DEG, 1.0, spriteSpanU, spriteSpanV, uvScale);
            applyGlintPass(framePixels, width, height, timeMs, SECONDARY_PERIOD_MS, SECONDARY_ROTATION_DEG, -1.0, spriteSpanU, spriteSpanV, uvScale);

            BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            frame.setRGB(0, 0, width, height, framePixels, 0, width);
            frames.add(frame);
        }

        return context.withAnimationFrames(frames)
                .toBuilder()
                .frameDelayMs(FRAME_DELAY_MS)
                .build();
    }

    private void applyGlintPass(int[] pixels, int width, int height, double timeMs, double periodMs, double rotationDeg,
                                double direction, double spriteSpanU, double spriteSpanV, double uvScale) {
        double offset = (timeMs % periodMs) / periodMs;
        double inverseScale = 1.0 / uvScale;
        double adjustedOffset = offset * SCROLL_SPEED;
        double radians = Math.toRadians(rotationDeg);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        int textureWidth = glintTexture.getWidth();
        int textureHeight = glintTexture.getHeight();
        float[] sampled = new float[4];

        for (int y = 0; y < height; y++) {
            double baseV = (double) y / height * spriteSpanV;
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                int argb = pixels[index];
                int alpha = (argb >>> 24) & 0xFF;
                if (alpha == 0) {
                    continue;
                }

                double baseU = (double) x / width * spriteSpanU;

                double rotatedU = baseU * cos - baseV * sin;
                double rotatedV = baseU * sin + baseV * cos;

                double translatedU = rotatedU + direction * adjustedOffset * inverseScale;
                double scaledU = translatedU * uvScale;
                double scaledV = rotatedV * uvScale;

                sampleGlint(scaledU, scaledV, textureWidth, textureHeight, sampled);
                float glintAlpha = sampled[3] * GLINT_INTENSITY;
                if (glintAlpha <= 0.0f) {
                    continue;
                }

                float baseR = ((argb >> 16) & 0xFF) / 255f;
                float baseG = ((argb >> 8) & 0xFF) / 255f;
                float baseB = (argb & 0xFF) / 255f;

                float addR = sampled[0] * GLINT_TINT[0] * glintAlpha;
                float addG = sampled[1] * GLINT_TINT[1] * glintAlpha;
                float addB = sampled[2] * GLINT_TINT[2] * glintAlpha;

                int outR = (int) (Math.min(1.0f, baseR + addR) * 255.0f + 0.5f);
                int outG = (int) (Math.min(1.0f, baseG + addG) * 255.0f + 0.5f);
                int outB = (int) (Math.min(1.0f, baseB + addB) * 255.0f + 0.5f);

                pixels[index] = (alpha << 24) | (outR << 16) | (outG << 8) | outB;
            }
        }
    }

    private void sampleGlint(double u, double v, int textureWidth, int textureHeight, float[] out) {
        double wrappedU = u - Math.floor(u);
        double wrappedV = v - Math.floor(v);

        double texX = wrappedU * textureWidth - 0.5;
        double texY = wrappedV * textureHeight - 0.5;

        double baseX = Math.floor(texX);
        double baseY = Math.floor(texY);

        int leftX = floorMod((int) baseX, textureWidth);
        int topY = floorMod((int) baseY, textureHeight);
        int rightX = (leftX + 1) % textureWidth;
        int bottomY = (topY + 1) % textureHeight;

        double fracX = texX - baseX;
        double fracY = texY - baseY;

        int topLeftColor = glintTexture.getRGB(leftX, topY);
        int topRightColor = glintTexture.getRGB(rightX, topY);
        int bottomLeftColor = glintTexture.getRGB(leftX, bottomY);
        int bottomRightColor = glintTexture.getRGB(rightX, bottomY);

        double weightTopLeft = (1.0 - fracX) * (1.0 - fracY);
        double weightTopRight = fracX * (1.0 - fracY);
        double weightBottomLeft = (1.0 - fracX) * fracY;
        double weightBottomRight = fracX * fracY;

        double red = ((topLeftColor >> 16) & 0xFF) * weightTopLeft
            + ((topRightColor >> 16) & 0xFF) * weightTopRight
            + ((bottomLeftColor >> 16) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >> 16) & 0xFF) * weightBottomRight;
        double green = ((topLeftColor >> 8) & 0xFF) * weightTopLeft
            + ((topRightColor >> 8) & 0xFF) * weightTopRight
            + ((bottomLeftColor >> 8) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >> 8) & 0xFF) * weightBottomRight;
        double blue = (topLeftColor & 0xFF) * weightTopLeft
            + (topRightColor & 0xFF) * weightTopRight
            + (bottomLeftColor & 0xFF) * weightBottomLeft
            + (bottomRightColor & 0xFF) * weightBottomRight;
        double alpha = ((topLeftColor >>> 24) & 0xFF) * weightTopLeft
            + ((topRightColor >>> 24) & 0xFF) * weightTopRight
            + ((bottomLeftColor >>> 24) & 0xFF) * weightBottomLeft
            + ((bottomRightColor >>> 24) & 0xFF) * weightBottomRight;

        out[0] = (float) (red / 255.0);
        out[1] = (float) (green / 255.0);
        out[2] = (float) (blue / 255.0);
        out[3] = (float) (alpha / 255.0);
    }

    private int floorMod(int value, int modulus) {
        int result = value % modulus;
        return result < 0 ? result + modulus : result;
    }

    private BufferedImage ensureArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();

        return converted;
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
