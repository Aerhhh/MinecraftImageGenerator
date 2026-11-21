package net.aerh.imagegenerator.effect.impl;

import net.aerh.imagegenerator.image.RenderCancellation;
import net.aerh.imagegenerator.image.RenderLimits;
import net.aerh.imagegenerator.effect.EffectContext;
import net.aerh.imagegenerator.effect.EffectResult;
import net.aerh.imagegenerator.effect.ImageEffect;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Enchantment glint effect - creates an animated glint overlay.
 * <p>
 * This effect generates an animated glint overlay similar to Minecraft's
 * enchantment effect.
 */
public class GlintImageEffect implements ImageEffect {

    private final int[] glintPixels;
    private final int glintTextureWidth;
    private final int glintTextureHeight;

    private static final int FRAME_DELAY_MS = 33; // ~30 FPS
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

    /**
     * Create glint effect.
     *
     * @param glintTexture The glint texture image
     */
    public GlintImageEffect(BufferedImage glintTexture) {
        if (glintTexture == null) {
            throw new IllegalArgumentException("Glint texture cannot be null");
        }

        this.glintTextureWidth = glintTexture.getWidth();
        this.glintTextureHeight = glintTexture.getHeight();
        this.glintPixels = glintTexture.getRGB(0, 0, glintTextureWidth, glintTextureHeight, null, 0, glintTextureWidth);
    }

    @Override
    public EffectResult apply(EffectContext context) {
        BufferedImage baseImage = ensureArgb(context.getImage());

        int frameCount = (int) Math.ceil((double) TOTAL_DURATION_MS / FRAME_DELAY_MS);
        int width = baseImage.getWidth();
        int height = baseImage.getHeight();
        RenderLimits.checkFrames(width, height, frameCount);
        int[] basePixels = baseImage.getRGB(0, 0, width, height, null, 0, width);

        double spriteSpanU = BASE_SPRITE_PIXELS / glintTextureWidth;
        double spriteSpanV = BASE_SPRITE_PIXELS / glintTextureHeight;
        double resolutionScale = Math.max(Math.max(width, height) / BASE_SPRITE_PIXELS, 1.0);
        double uvScale = UV_SCALE / resolutionScale;
        double[] baseU = new double[width];
        double[] baseV = new double[height];
        for (int x = 0; x < width; x++) {
            baseU[x] = (double) x / width * spriteSpanU;
        }
        for (int y = 0; y < height; y++) {
            baseV[y] = (double) y / height * spriteSpanV;
        }

        List<BufferedImage> frames = new ArrayList<>(frameCount);

        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            RenderCancellation.check();
            double timeMs = frameIndex * FRAME_DELAY_MS;
            int[] framePixels = Arrays.copyOf(basePixels, basePixels.length);

            applyGlintPass(framePixels, width, height, timeMs, PRIMARY_PERIOD_MS, PRIMARY_ROTATION_DEG, 1.0, baseU, baseV, uvScale);
            applyGlintPass(framePixels, width, height, timeMs, SECONDARY_PERIOD_MS, SECONDARY_ROTATION_DEG, -1.0, baseU, baseV, uvScale);

            BufferedImage frame = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            frame.setRGB(0, 0, width, height, framePixels, 0, width);
            frames.add(frame);
        }

        return EffectResult.animated(frames, FRAME_DELAY_MS);
    }

    private void applyGlintPass(int[] pixels, int width, int height, double timeMs, double periodMs, double rotationDeg,
                                double direction, double[] baseU, double[] baseV, double uvScale) {
        double offset = (timeMs % periodMs) / periodMs;
        double inverseScale = 1.0 / uvScale;
        double adjustedOffset = offset * SCROLL_SPEED;
        double radians = Math.toRadians(rotationDeg);
        double cos = Math.cos(radians);
        double sin = Math.sin(radians);
        float[] sampled = new float[4];

        for (int y = 0; y < height; y++) {
            double yBaseV = baseV[y];
            int preIndex = y * width;
            int preMax = preIndex + width;
            for (int index = preIndex; index < preMax; index++) {
                int argb = pixels[index];
                int alpha = (argb >>> 24);
                if (alpha == 0) {
                    continue;
                }

                double xBaseU = baseU[index - preIndex];

                double rotatedU = xBaseU * cos - yBaseV * sin;
                double rotatedV = xBaseU * sin + yBaseV * cos;

                double translatedU = rotatedU + direction * adjustedOffset * inverseScale;
                double scaledU = translatedU * uvScale;
                double scaledV = rotatedV * uvScale;

                sampleGlint(scaledU, scaledV, sampled);
                float sampledAlpha = sampled[3];
                if (sampledAlpha <= 0.0f) {
                    continue;
                }
                float glintAlpha = sampledAlpha * GLINT_INTENSITY;

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

    private void sampleGlint(double u, double v, float[] out) {
        double wrappedU = u - Math.floor(u);
        double wrappedV = v - Math.floor(v);

        double texX = wrappedU * glintTextureWidth - 0.5;
        double texY = wrappedV * glintTextureHeight - 0.5;

        double baseX = Math.floor(texX);
        double baseY = Math.floor(texY);

        int leftX = floorMod((int) baseX, glintTextureWidth);
        int topY = floorMod((int) baseY, glintTextureHeight);
        int rightX = (leftX + 1) % glintTextureWidth;
        int bottomY = (topY + 1) % glintTextureHeight;

        double fracX = texX - baseX;
        double fracY = texY - baseY;

        int topLeftColor = glintPixels[topY * glintTextureWidth + leftX];
        int topRightColor = glintPixels[topY * glintTextureWidth + rightX];
        int bottomLeftColor = glintPixels[bottomY * glintTextureWidth + leftX];
        int bottomRightColor = glintPixels[bottomY * glintTextureWidth + rightX];

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

    @Override
    public String getName() {
        return "glint";
    }

    @Override
    public boolean canApply(EffectContext context) {
        return context.isEnchanted();
    }
}