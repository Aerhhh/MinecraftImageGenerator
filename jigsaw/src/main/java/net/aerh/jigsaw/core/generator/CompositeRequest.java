package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Input request that composes multiple sub-requests into a single rendered image.
 *
 * <p>The engine renders each sub-request independently and then composes the results
 * according to the specified {@link Layout} and padding. Sub-requests may themselves be
 * {@code CompositeRequest}s, enabling naturally recursive composition.
 *
 * @param requests    the sub-requests to render and compose; must not be {@code null} or empty
 * @param layout      whether to stack images vertically or side by side horizontally
 * @param padding     pixel gap between adjacent results (and around the outer edge)
 * @param scaleFactor pixel scale multiplier applied to all sub-results before compositing;
 *                    must be in {@code [1, 64]}
 */
public record CompositeRequest(
        List<RenderRequest> requests,
        Layout layout,
        int padding,
        int scaleFactor
) implements RenderRequest {

    /**
     * Determines how multiple results are arranged within the composed image.
     */
    public enum Layout {
        /**
         * Stack images top-to-bottom.
         */
        VERTICAL,
        /**
         * Place images side by side.
         */
        HORIZONTAL
    }

    public CompositeRequest {
        Objects.requireNonNull(requests, "requests must not be null");
        Objects.requireNonNull(layout, "layout must not be null");
        if (padding < 0) {
            throw new IllegalArgumentException("padding must be >= 0, got: " + padding);
        }
        if (scaleFactor < 1) {
            throw new IllegalArgumentException("scaleFactor must be >= 1, got: " + scaleFactor);
        }
        if (scaleFactor > 64) {
            throw new IllegalArgumentException("scaleFactor must be <= 64, got: " + scaleFactor);
        }
        requests = List.copyOf(requests);
    }

    /**
     * Convenience constructor without {@code scaleFactor} (defaults to {@code 1}).
     *
     * @param requests the sub-requests
     * @param layout   the layout direction
     * @param padding  pixel gap between results
     */
    public CompositeRequest(List<RenderRequest> requests, Layout layout, int padding) {
        this(requests, layout, padding, 1);
    }

    /**
     * {@inheritDoc}
     *
     * <p>If this request already has an explicit {@code scaleFactor} (i.e. not the default of
     * {@code 1}), the inherited value is ignored and this request is returned unchanged.
     */
    @Override
    public RenderRequest withInheritedScale(int scaleFactor) {
        if (this.scaleFactor != 1) {
            return this;
        }
        return new CompositeRequest(requests, layout, padding, scaleFactor);
    }

    /**
     * Returns a builder with default values (HORIZONTAL layout, 4px padding).
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link CompositeRequest}.
     */
    public static final class Builder {

        private final List<RenderRequest> requests = new ArrayList<>();
        private Layout layout = Layout.HORIZONTAL;
        private int padding = 4;
        private int scaleFactor = 1;

        private Builder() {
        }

        /**
         * Appends a sub-request to the end of the request list.
         *
         * @param request the sub-request to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder add(RenderRequest request) {
            this.requests.add(Objects.requireNonNull(request, "request must not be null"));
            return this;
        }

        /**
         * Inserts a sub-request at the specified position.
         *
         * <p>This supports positional composition where, for example, a tooltip needs to
         * appear before or after an item image in the final layout.
         *
         * @param index   the position at which to insert; must be in {@code [0, size()]}
         * @param request the sub-request to insert; must not be {@code null}
         * @return this builder for chaining
         * @throws IndexOutOfBoundsException if the index is out of range
         */
        public Builder add(int index, RenderRequest request) {
            Objects.requireNonNull(request, "request must not be null");
            this.requests.add(index, request);
            return this;
        }

        /**
         * Appends all sub-requests from the given list.
         *
         * @param requests the sub-requests to add; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder addAll(List<? extends RenderRequest> requests) {
            Objects.requireNonNull(requests, "requests must not be null");
            for (RenderRequest request : requests) {
                Objects.requireNonNull(request, "request elements must not be null");
            }
            this.requests.addAll(requests);
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
         * Sets the pixel scale multiplier applied to all sub-results before compositing.
         *
         * @param val the scale factor; must be in {@code [1, 64]}
         * @return this builder for chaining
         */
        public Builder scaleFactor(int val) {
            this.scaleFactor = val;
            return this;
        }

        /**
         * Builds the {@link CompositeRequest}.
         *
         * @return a new request
         */
        public CompositeRequest build() {
            return new CompositeRequest(requests, layout, padding, scaleFactor);
        }
    }
}
