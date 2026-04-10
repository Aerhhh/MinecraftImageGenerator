package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Input request for the {@link TooltipGenerator}.
 *
 * <p>Use {@link #builder()} to construct instances. All fields have sensible defaults.
 *
 * @param lines               pre-formatted lines with {@code &} or {@code §} color codes;
 *                            the generator renders them as-is without any placeholder parsing
 * @param alpha               the background alpha value (0-255)
 * @param padding             the padding in pixels around the tooltip
 * @param firstLinePadding    whether to add extra padding below the first line
 * @param maxLineLength       the maximum visible character count per line before wrapping
 * @param centeredText        whether each line should be horizontally centered
 * @param renderBorder        whether to render the Minecraft-style tooltip border
 * @param scaleFactor         integer scale multiplier applied to all coordinates
 */
public record TooltipRequest(
        List<String> lines,
        int alpha,
        int padding,
        boolean firstLinePadding,
        int maxLineLength,
        boolean centeredText,
        boolean renderBorder,
        int scaleFactor
) implements RenderRequest {

    /** Default background alpha value. */
    public static final int DEFAULT_ALPHA = 255;

    /** Default tooltip padding in pixels. */
    public static final int DEFAULT_PADDING = 7;

    /** Default maximum visible character count per line. */
    public static final int DEFAULT_MAX_LINE_LENGTH = 38;

    /** Minimum allowed value for {@code maxLineLength}. */
    public static final int MIN_LINE_LENGTH = 1;

    public TooltipRequest {
        Objects.requireNonNull(lines, "lines must not be null");
        lines = Collections.unmodifiableList(new ArrayList<>(lines));
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha must be between 0 and 255, got: " + alpha);
        }
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("scaleFactor must be >= 1, got: " + scaleFactor);
        }
    }

    @Override
    public RenderRequest withInheritedScale(int scaleFactor) {
        if (this.scaleFactor != 1) {
            return this;
        }
        return new TooltipRequest(lines, alpha, padding, firstLinePadding, maxLineLength,
                centeredText, renderBorder, scaleFactor);
    }

    /**
     * Returns a new {@link Builder} for constructing a {@link TooltipRequest}.
     *
     * @return a new builder with default values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link TooltipRequest}.
     */
    public static final class Builder {

        private final List<String> lines = new ArrayList<>();
        private int alpha = DEFAULT_ALPHA;
        private int padding = DEFAULT_PADDING;
        private boolean firstLinePadding = true;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private boolean centeredText = false;
        private boolean renderBorder = true;
        private int scaleFactor = 1;

        private Builder() {
        }

        /**
         * Adds a single pre-formatted line (with {@code &} or {@code §} color codes) to the tooltip.
         *
         * @param line the line to add; must not be {@code null}
         * @return this builder
         */
        public Builder line(String line) {
            this.lines.add(Objects.requireNonNull(line, "line must not be null"));
            return this;
        }

        /**
         * Adds multiple pre-formatted lines to the tooltip.
         *
         * @param lines the lines to add; must not be {@code null}
         * @return this builder
         */
        public Builder lines(List<String> lines) {
            Objects.requireNonNull(lines, "lines must not be null");
            this.lines.addAll(lines);
            return this;
        }

        /**
         * Sets the background alpha value. Clamped to [0, 255].
         *
         * @param val the alpha value
         * @return this builder
         */
        public Builder alpha(int val) {
            this.alpha = Math.max(0, Math.min(255, val));
            return this;
        }

        /**
         * Sets the tooltip padding in pixels.
         *
         * @param val the padding; negative values are treated as 0
         * @return this builder
         */
        public Builder padding(int val) {
            this.padding = Math.max(0, val);
            return this;
        }

        /**
         * Sets whether to add extra vertical padding below the first line.
         *
         * @param val {@code true} to enable first-line padding
         * @return this builder
         */
        public Builder firstLinePadding(boolean val) {
            this.firstLinePadding = val;
            return this;
        }

        /**
         * Sets the maximum visible character count per line.
         * Values less than {@value #MIN_LINE_LENGTH} are treated as {@value #MIN_LINE_LENGTH}.
         *
         * @param val the max line length
         * @return this builder
         */
        public Builder maxLineLength(int val) {
            this.maxLineLength = Math.max(MIN_LINE_LENGTH, val);
            return this;
        }

        /**
         * Sets whether each line should be horizontally centered.
         *
         * @param val {@code true} to center text
         * @return this builder
         */
        public Builder centeredText(boolean val) {
            this.centeredText = val;
            return this;
        }

        /**
         * Sets whether to render the Minecraft-style tooltip border.
         *
         * @param val {@code true} to render the border
         * @return this builder
         */
        public Builder renderBorder(boolean val) {
            this.renderBorder = val;
            return this;
        }

        /**
         * Sets the integer scale multiplier applied to all pixel coordinates.
         * Values less than 1 are treated as 1.
         *
         * @param val the scale factor
         * @return this builder
         */
        public Builder scaleFactor(int val) {
            this.scaleFactor = Math.max(1, val);
            return this;
        }

        /**
         * Builds the {@link TooltipRequest}.
         *
         * @return a new request
         */
        public TooltipRequest build() {
            return new TooltipRequest(
                    lines, alpha, padding, firstLinePadding,
                    maxLineLength, centeredText, renderBorder,
                    scaleFactor
            );
        }
    }
}
