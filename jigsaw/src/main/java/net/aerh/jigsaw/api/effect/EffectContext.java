package net.aerh.jigsaw.api.effect;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable context passed through the image effect pipeline.
 *
 * <p>An {@code EffectContext} carries the item image, animation frames, item metadata, and
 * rendering flags that each {@link ImageEffect} may inspect or transform. Because the context is
 * immutable, effects must use the {@code with*} copy methods to produce modified versions rather
 * than mutating the object in place.
 *
 * <p>Constructing a context:
 * <pre>{@code
 * EffectContext ctx = EffectContext.builder()
 *         .image(baseImage)
 *         .itemId("diamond_sword")
 *         .enchanted(true)
 *         .build();
 * }</pre>
 *
 * <p>Deriving a modified copy inside an effect:
 * <pre>{@code
 * public EffectContext apply(EffectContext context) {
 *     BufferedImage tinted = applyTint(context.image());
 *     return context.withImage(tinted);
 * }
 * }</pre>
 *
 * @see ImageEffect
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

    /**
     * Returns a new, empty {@link Builder} for constructing an {@code EffectContext} from scratch.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the current item image to be transformed by the effect pipeline.
     *
     * @return the item image; may be {@code null} before the image is first assigned
     */
    public BufferedImage image() {
        return image;
    }

    /**
     * Returns the ordered list of animation frames, or an empty list for non-animated items.
     *
     * @return an unmodifiable list of animation frames
     */
    public List<BufferedImage> animationFrames() {
        return animationFrames;
    }

    /**
     * Returns the delay between animation frames in milliseconds.
     * Returns {@code 0} when the item is not animated.
     *
     * @return frame delay in milliseconds
     */
    public int frameDelayMs() {
        return frameDelayMs;
    }

    /**
     * Returns the Minecraft item ID (e.g. {@code "diamond_sword"}) for the item being rendered.
     *
     * @return the item ID; may be {@code null} if not set
     */
    public String itemId() {
        return itemId;
    }

    /**
     * Returns {@code true} if the item has an enchantment glint that should be applied.
     *
     * @return {@code true} if the item is enchanted
     */
    public boolean enchanted() {
        return enchanted;
    }

    /**
     * Returns {@code true} if the item is currently in a hovered state (e.g. a hover highlight
     * effect should be applied).
     *
     * @return {@code true} if the item is hovered
     */
    public boolean hovered() {
        return hovered;
    }

    /**
     * Returns the full metadata map as an unmodifiable view.
     * Prefer {@link #metadata(String, Class)} for type-safe access to individual values.
     *
     * @return an unmodifiable map of metadata key-value pairs
     */
    public Map<String, Object> metadata() {
        return metadata;
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
     * Returns the metadata value for the given key cast to the given type, or empty if the key is
     * absent or the stored value is not an instance of {@code type}.
     *
     * @param <T>  the expected value type
     * @param key  the metadata key to look up
     * @param type the expected class of the stored value
     * @return an {@link Optional} containing the cast value, or empty if absent or incompatible
     */
    public <T> Optional<T> metadata(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (type.isInstance(value)) {
            return Optional.of(type.cast(value));
        }
        return Optional.empty();
    }

    /**
     * Returns a {@link Builder} pre-populated with all values from this context.
     * Use this to produce a modified copy without affecting the original:
     *
     * <pre>{@code
     * EffectContext updated = context.toBuilder().enchanted(false).build();
     * }</pre>
     *
     * @return a new builder initialised from this context
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

    /**
     * Builder for {@link EffectContext}.
     *
     */
    public static final class Builder {

        private BufferedImage image;
        private List<BufferedImage> animationFrames = new ArrayList<>();
        private int frameDelayMs = 0;
        private String itemId;
        private boolean enchanted = false;
        private boolean hovered = false;
        private Map<String, Object> metadata = new HashMap<>();

        private Builder() {}

        /**
         * Sets the item image.
         *
         * @param val the image to use
         *
         * @return this builder
         */
        public Builder image(BufferedImage val) {
            this.image = val;
            return this;
        }

        /**
         * Sets the animation frames. The list is copied defensively.
         *
         * @param val the ordered list of frames; must not be {@code null}
         * @return this builder
         */
        public Builder animationFrames(List<BufferedImage> val) {
            this.animationFrames = new ArrayList<>(val);
            return this;
        }

        /**
         * Sets the delay between animation frames in milliseconds.
         *
         * @param val the frame delay; use {@code 0} for non-animated items
         * @return this builder
         */
        public Builder frameDelayMs(int val) {
            this.frameDelayMs = val;
            return this;
        }

        /**
         * Sets the Minecraft item ID (e.g. {@code "diamond_sword"}).
         *
         * @param val the item ID
         * @return this builder
         */
        public Builder itemId(String val) {
            this.itemId = val;
            return this;
        }

        /**
         * Sets whether the item has an enchantment glint.
         *
         * @param val {@code true} if the item is enchanted
         * @return this builder
         */
        public Builder enchanted(boolean val) {
            this.enchanted = val;
            return this;
        }

        /**
         * Sets whether the item is in a hovered state.
         *
         * @param val {@code true} if the item is hovered
         * @return this builder
         */
        public Builder hovered(boolean val) {
            this.hovered = val;
            return this;
        }

        /**
         * Sets the entire metadata map, replacing any previously set entries. The map is copied
         * defensively.
         *
         * @param val the metadata map; must not be {@code null}
         * @return this builder
         */
        public Builder metadata(Map<String, Object> val) {
            this.metadata = new HashMap<>(val);
            return this;
        }

        /**
         * Builds and returns the configured {@link EffectContext}.
         *
         * @return a new immutable {@code EffectContext}
         * @throws NullPointerException if {@code image} has not been set
         */
        public EffectContext build() {
            Objects.requireNonNull(image, "image must not be null");
            return new EffectContext(this);
        }
    }
}
