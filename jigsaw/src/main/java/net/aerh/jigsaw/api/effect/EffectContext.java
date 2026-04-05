package net.aerh.jigsaw.api.effect;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable context passed through the image effect pipeline.
 * <p>
 * Use {@link #builder()} to construct, and {@link #withImage(BufferedImage)},
 * {@link #withAnimationFrames(List)}, {@link #withMetadata(String, Object)} to
 * derive modified copies without mutating the original.
 */
public final class EffectContext {

    private final BufferedImage image;
    private final List<BufferedImage> animationFrames;
    private final int frameDelayMs;
    private final String itemId;
    private final boolean enchanted;
    private final boolean hovered;
    private final Map<String, Object> metadata;

    private EffectContext(Builder builder) {
        this.image = builder.image;
        this.animationFrames = Collections.unmodifiableList(new ArrayList<>(builder.animationFrames));
        this.frameDelayMs = builder.frameDelayMs;
        this.itemId = builder.itemId;
        this.enchanted = builder.enchanted;
        this.hovered = builder.hovered;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
    }

    public BufferedImage image() {
        return image;
    }

    public List<BufferedImage> animationFrames() {
        return animationFrames;
    }

    public int frameDelayMs() {
        return frameDelayMs;
    }

    public String itemId() {
        return itemId;
    }

    public boolean enchanted() {
        return enchanted;
    }

    public boolean hovered() {
        return hovered;
    }

    public Map<String, Object> metadata() {
        return metadata;
    }

    /**
     * Returns the metadata value for the given key cast to the given type, or empty if absent or wrong type.
     */
    public <T> Optional<T> metadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Returns a new {@code EffectContext} with the image replaced, all other fields unchanged.
     */
    public EffectContext withImage(BufferedImage newImage) {
        return toBuilder().image(newImage).build();
    }

    /**
     * Returns a new {@code EffectContext} with the animation frames replaced, all other fields unchanged.
     */
    public EffectContext withAnimationFrames(List<BufferedImage> frames) {
        return toBuilder().animationFrames(frames).build();
    }

    /**
     * Returns a new {@code EffectContext} with the given metadata entry added or replaced.
     * The original context is not modified.
     */
    public EffectContext withMetadata(String key, Object value) {
        Builder b = toBuilder();
        b.metadata.put(key, value);
        return b.build();
    }

    /**
     * Returns a builder pre-populated with this context's values for copy-on-write semantics.
     */
    public Builder toBuilder() {
        Builder b = new Builder();
        b.image = this.image;
        b.animationFrames = new ArrayList<>(this.animationFrames);
        b.frameDelayMs = this.frameDelayMs;
        b.itemId = this.itemId;
        b.enchanted = this.enchanted;
        b.hovered = this.hovered;
        b.metadata = new HashMap<>(this.metadata);
        return b;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private BufferedImage image;
        private List<BufferedImage> animationFrames = new ArrayList<>();
        private int frameDelayMs = 0;
        private String itemId;
        private boolean enchanted = false;
        private boolean hovered = false;
        private Map<String, Object> metadata = new HashMap<>();

        private Builder() {}

        public Builder image(BufferedImage val) {
            this.image = val;
            return this;
        }

        public Builder animationFrames(List<BufferedImage> val) {
            this.animationFrames = new ArrayList<>(val);
            return this;
        }

        public Builder frameDelayMs(int val) {
            this.frameDelayMs = val;
            return this;
        }

        public Builder itemId(String val) {
            this.itemId = val;
            return this;
        }

        public Builder enchanted(boolean val) {
            this.enchanted = val;
            return this;
        }

        public Builder hovered(boolean val) {
            this.hovered = val;
            return this;
        }

        public Builder metadata(Map<String, Object> val) {
            this.metadata = new HashMap<>(val);
            return this;
        }

        public EffectContext build() {
            return new EffectContext(this);
        }
    }
}
