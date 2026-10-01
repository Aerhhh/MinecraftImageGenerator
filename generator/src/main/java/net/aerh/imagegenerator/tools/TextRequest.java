package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.image.MinecraftTooltip;
import net.aerh.imagegenerator.impl.tooltip.MinecraftTooltipGenerator;
import net.aerh.imagegenerator.pack.PackId;
import net.aerh.imagegenerator.tools.support.Required;
import net.aerh.imagegenerator.tools.support.Strings;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Options of the text tool. Defaults are those of the Discord {@code /gen text} command. */
public record TextRequest(String text, boolean centered, int alpha, int padding, int maxLineLength, boolean renderBorder,
                          @Nullable PackId packId, @Nullable String tooltipStyle) {

    public static final boolean DEFAULT_CENTERED = false;
    public static final int DEFAULT_ALPHA = 0;
    public static final int DEFAULT_PADDING = MinecraftTooltip.DEFAULT_PADDING;
    public static final int DEFAULT_MAX_LINE_LENGTH = MinecraftTooltipGenerator.DEFAULT_MAX_LINE_LENGTH * 3;
    public static final boolean DEFAULT_RENDER_BORDER = false;

    public TextRequest {
        Required.check(text, "text");
        tooltipStyle = Strings.blankToNull(tooltipStyle);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Passing null to a defaulted option (the boxed number and boolean setters) leaves its value unchanged; passing null to an optional text or pack setter clears it. */
    public static final class Builder {
        private String text;
        private boolean centered = DEFAULT_CENTERED;
        private int alpha = DEFAULT_ALPHA;
        private int padding = DEFAULT_PADDING;
        private int maxLineLength = DEFAULT_MAX_LINE_LENGTH;
        private boolean renderBorder = DEFAULT_RENDER_BORDER;
        private PackId packId;
        private String tooltipStyle;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder centered(@Nullable Boolean centered) {
            this.centered = Objects.requireNonNullElse(centered, this.centered);
            return this;
        }

        public Builder alpha(@Nullable Integer alpha) {
            this.alpha = Objects.requireNonNullElse(alpha, this.alpha);
            return this;
        }

        public Builder padding(@Nullable Integer padding) {
            this.padding = Objects.requireNonNullElse(padding, this.padding);
            return this;
        }

        public Builder maxLineLength(@Nullable Integer maxLineLength) {
            this.maxLineLength = Objects.requireNonNullElse(maxLineLength, this.maxLineLength);
            return this;
        }

        public Builder renderBorder(@Nullable Boolean renderBorder) {
            this.renderBorder = Objects.requireNonNullElse(renderBorder, this.renderBorder);
            return this;
        }

        public Builder packId(@Nullable PackId packId) {
            this.packId = packId;
            return this;
        }

        public Builder tooltipStyle(@Nullable String tooltipStyle) {
            this.tooltipStyle = tooltipStyle;
            return this;
        }

        public TextRequest build() {
            return new TextRequest(text, centered, alpha, padding, maxLineLength, renderBorder, packId, tooltipStyle);
        }
    }
}
