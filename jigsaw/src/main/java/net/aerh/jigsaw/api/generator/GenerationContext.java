package net.aerh.jigsaw.api.generator;

import java.util.function.Consumer;

/**
 * Caller-supplied options that control a single rendering invocation.
 *
 * <p>Use {@link #defaults()} for standard rendering, or {@link #builder()} to customise
 * specific options:
 *
 * <pre>{@code
 * GenerationContext context = GenerationContext.builder()
 *         .skipCache(true)
 *         .feedback(msg -> System.out.println("[jigsaw] " + msg))
 *         .build();
 * GeneratorResult result = engine.renderItem("diamond_sword", context);
 * }</pre>
 *
 * @param skipCache whether the engine should bypass cached results and force a fresh render
 * @param feedback  a consumer that receives diagnostic or progress messages during rendering;
 *                  may be a no-op but must not be {@code null}
 *
 * @see net.aerh.jigsaw.api.Engine#renderItem(String, GenerationContext)
 */
public record GenerationContext(boolean skipCache, Consumer<String> feedback) {

    private static final GenerationContext DEFAULTS = new GenerationContext(false, msg -> {});

    /**
     * Returns a shared {@code GenerationContext} with all defaults: caching enabled, no-op feedback.
     *
     * @return the default context instance
     */
    public static GenerationContext defaults() {
        return DEFAULTS;
    }

    /**
     * Returns a new {@link Builder} for constructing a customised {@code GenerationContext}.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link GenerationContext}.
     *
     */
    public static final class Builder {

        private boolean skipCache = false;
        private Consumer<String> feedback = msg -> {};

        private Builder() {
        }

        /**
         * Sets whether the engine should bypass the cache and force a fresh render.
         *
         * @param val {@code true} to skip the cache
         * @return this builder
         */
        public Builder skipCache(boolean val) {
            this.skipCache = val;
            return this;
        }

        /**
         * Sets the feedback consumer that receives diagnostic messages during rendering.
         *
         * @param val the consumer; must not be {@code null}
         * @return this builder
         */
        public Builder feedback(Consumer<String> val) {
            this.feedback = val;
            return this;
        }

        /**
         * Builds and returns the configured {@link GenerationContext}.
         *
         * @return a new {@code GenerationContext}
         */
        public GenerationContext build() {
            return new GenerationContext(skipCache, feedback);
        }
    }
}
