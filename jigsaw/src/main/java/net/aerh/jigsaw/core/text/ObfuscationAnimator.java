package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextRenderOptions;
import net.aerh.jigsaw.api.text.TextStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Generates an animated {@link GeneratorResult} where obfuscated text segments cycle through
 * random characters at each frame, mimicking Minecraft's obfuscated text rendering.
 *
 * <p>Non-obfuscated segments are identical across all frames. Obfuscated segments are replaced
 * with random printable ASCII characters (0x21-0x7E) of the same length on every frame.
 *
 * <p>Default frame rate is 10 FPS (100ms frame delay).
 */
public final class ObfuscationAnimator {

    /** Default frames per second for the obfuscation animation. */
    public static final int DEFAULT_FPS = 10;

    /** Default number of frames to generate for the animation. */
    public static final int DEFAULT_FRAME_COUNT = 10;

    private static final int FRAME_DELAY_MS_AT_DEFAULT_FPS = 1000 / DEFAULT_FPS;

    // Printable ASCII range used for random character substitution
    private static final char OBFUSCATED_CHAR_MIN = '!';
    private static final char OBFUSCATED_CHAR_MAX = '~';

    private final TextLayout layout;
    private final TextRenderOptions options;
    private final int frameCount;
    private final int frameDelayMs;
    private final Random random;

    /**
     * Creates an animator with the default frame count and frame rate.
     *
     * @param layout  the laid-out text to animate; must not be {@code null}
     * @param options the render options for each frame; must not be {@code null}
     */
    public ObfuscationAnimator(TextLayout layout, TextRenderOptions options) {
        this(layout, options, DEFAULT_FRAME_COUNT, FRAME_DELAY_MS_AT_DEFAULT_FPS);
    }

    /**
     * Creates an animator with a custom frame count and delay.
     *
     * @param layout       the laid-out text to animate; must not be {@code null}
     * @param options      the render options for each frame; must not be {@code null}
     * @param frameCount   the number of animation frames to generate; must be &gt;= 1
     * @param frameDelayMs the delay between frames in milliseconds; must be &gt; 0
     */
    public ObfuscationAnimator(TextLayout layout, TextRenderOptions options, int frameCount, int frameDelayMs) {
        this.layout = Objects.requireNonNull(layout, "layout must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
        if (frameCount < 1) {
            throw new IllegalArgumentException("frameCount must be >= 1, got: " + frameCount);
        }
        if (frameDelayMs <= 0) {
            throw new IllegalArgumentException("frameDelayMs must be > 0, got: " + frameDelayMs);
        }
        this.frameCount = frameCount;
        this.frameDelayMs = frameDelayMs;
        this.random = new Random();
    }

    /**
     * Generates the animation frames and returns them as an {@link GeneratorResult.AnimatedImage}.
     *
     * @return the animated result; never {@code null}
     */
    public GeneratorResult.AnimatedImage animate() {
        List<java.awt.image.BufferedImage> frames = new ArrayList<>(frameCount);

        for (int i = 0; i < frameCount; i++) {
            TextLayout frameLayout = buildFrameLayout();
            GeneratorResult rendered = MinecraftTextRenderer.renderLayout(frameLayout, options);
            frames.add(rendered.firstFrame());
        }

        return new GeneratorResult.AnimatedImage(frames, frameDelayMs);
    }

    /**
     * Builds a {@link TextLayout} for one animation frame by randomizing obfuscated segments.
     */
    private TextLayout buildFrameLayout() {
        List<TextLine> frameLines = new ArrayList<>(layout.lines().size());

        for (TextLine line : layout.lines()) {
            List<TextSegment> frameSegments = new ArrayList<>(line.segments().size());
            for (TextSegment segment : line.segments()) {
                if (segment.style().obfuscated()) {
                    String randomized = randomize(segment.text());
                    frameSegments.add(new TextSegment(randomized, segment.style()));
                } else {
                    frameSegments.add(segment);
                }
            }
            frameLines.add(new TextLine(frameSegments, line.width()));
        }

        return new TextLayout(frameLines, layout.width(), layout.height());
    }

    /**
     * Replaces every character in {@code text} with a random printable ASCII character.
     */
    private String randomize(String text) {
        int len = text.length();
        char[] buf = new char[len];
        int range = OBFUSCATED_CHAR_MAX - OBFUSCATED_CHAR_MIN + 1;
        for (int i = 0; i < len; i++) {
            buf[i] = (char) (OBFUSCATED_CHAR_MIN + random.nextInt(range));
        }
        return new String(buf);
    }
}
