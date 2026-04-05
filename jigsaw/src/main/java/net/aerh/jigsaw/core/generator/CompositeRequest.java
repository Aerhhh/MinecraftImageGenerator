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

    /** Determines how multiple results are arranged. */
    public enum Layout {
        VERTICAL,
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

    public static final class Builder {

        private final List<GeneratorResult> results = new ArrayList<>();
        private Layout layout = Layout.VERTICAL;
        private int padding = 4;

        private Builder() {}

        public Builder result(GeneratorResult val) {
            this.results.add(Objects.requireNonNull(val, "result must not be null"));
            return this;
        }

        public Builder results(List<GeneratorResult> val) {
            this.results.addAll(Objects.requireNonNull(val, "results must not be null"));
            return this;
        }

        public Builder layout(Layout val) {
            this.layout = Objects.requireNonNull(val, "layout must not be null");
            return this;
        }

        public Builder padding(int val) {
            this.padding = val;
            return this;
        }

        public CompositeRequest build() {
            return new CompositeRequest(results, layout, padding);
        }
    }
}
