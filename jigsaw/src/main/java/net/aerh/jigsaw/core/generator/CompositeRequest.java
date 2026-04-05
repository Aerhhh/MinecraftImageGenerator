package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GeneratorResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Input request for {@link CompositeGenerator}.
 *
 * @param results  the generator results to compose into a single image
 * @param layout   whether to stack images vertically or side by side horizontally
 * @param padding  pixel gap between adjacent results (and around the outer edge)
 */
public record CompositeRequest(
        List<GeneratorResult> results,
        Layout layout,
        int padding
) {

    /**
     * Determines how multiple results are arranged within the composed image.
     */
    public enum Layout {
        /**
         * Stack images top-to-bottom.
         */
        VERTICAL,
        /** Place images side by side. */
        HORIZONTAL
    }

    public CompositeRequest {
        Objects.requireNonNull(results, "results must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be >= 0, got: " + padding);
        }
        results = List.copyOf(results);
    }

    /** Returns a builder with default values (VERTICAL layout, 4px padding). */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CompositeRequest}.
     */
    public static final class Builder {

        private final List<GeneratorResult> results = new ArrayList<>();
        private Layout layout = Layout.VERTICAL;
        private int padding = 4;

        private Builder() {
        }

        /**
         * Adds a single result to compose.
         *
         * @param val the result to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder result(GeneratorResult val) {
            this.results.add(Objects.requireNonNull(val, "result must not be null"));
            return this;
        }

        /**
         * Adds all results from the given list.
         *
         * @param val the results to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder results(List<GeneratorResult> val) {
            this.results.addAll(Objects.requireNonNull(val, "results must not be null"));
            return this;
        }

        /**
         * Sets the layout direction.
         *
         * @param val the layout to use; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder layout(Layout val) {
            this.layout = Objects.requireNonNull(val, "layout must not be null");
            return this;
        }

        /**
         * Sets the pixel gap between adjacent results.
         *
         * @param val the padding in pixels; must be {@code >= 0}
         * @return this builder for chaining
         */
        public Builder padding(int val) {
            this.padding = val;
            return this;
        }

        /**
         * Builds the {@link CompositeRequest}.
         *
         * @return a new request
         */
        public CompositeRequest build() {
            return new CompositeRequest(results, layout, padding);
        }
    }
}
