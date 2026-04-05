package net.aerh.jigsaw.api.generator;

import java.util.function.Consumer;

public record GenerationContext(boolean skipCache, Consumer<String> feedback) {

    private static final GenerationContext DEFAULTS = new GenerationContext(false, msg -> {});

    public static GenerationContext defaults() {
        return DEFAULTS;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private boolean skipCache = false;
        private Consumer<String> feedback = msg -> {};

        private Builder() {}

        public Builder skipCache(boolean val) {
            this.skipCache = val;
            return this;
        }

        public Builder feedback(Consumer<String> val) {
            this.feedback = val;
            return this;
        }

        public GenerationContext build() {
            return new GenerationContext(skipCache, feedback);
        }
    }
}
