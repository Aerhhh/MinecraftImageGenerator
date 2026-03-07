package net.aerh.imagegenerator.impl.nbt;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable metadata extracted from an item's NBT by a {@link NbtFormatHandler}.
 * <p>
 * Values are stored as a simple string-keyed map. The well-known keys are defined as constants
 * on this class; handlers may add additional keys if needed.
 *
 * @see NbtFormatHandler#extractMetadata(JsonObject)
 */
public final class NbtFormatMetadata {

    /** Base64-encoded skin texture string for player heads. */
    public static final String KEY_PLAYER_HEAD_TEXTURE = "player_head_texture";

    /** Maximum visible character length across all lore lines, used for tooltip width. */
    public static final String KEY_MAX_LINE_LENGTH = "max_line_length";

    /** Whether the item has enchantments or an enchantment glint override. */
    public static final String KEY_ENCHANTED = "enchanted";

    /** Shared empty instance returned when a handler extracts no metadata. */
    public static final NbtFormatMetadata EMPTY = new NbtFormatMetadata(Collections.emptyMap());

    private final Map<String, Object> data;

    private NbtFormatMetadata(Map<String, Object> data) {
        this.data = data == null || data.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(data);
    }

    /** Creates a new builder for constructing metadata instances. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns {@code true} if no metadata values were extracted. */
    public boolean isEmpty() {
        return data.isEmpty();
    }

    /** Returns {@code true} if a value is present for the given key. */
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    /** Returns an unmodifiable view of the underlying data map. */
    public Map<String, Object> asMap() {
        return data;
    }

    /**
     * Returns the value for the given key, cast to the expected type.
     *
     * @param key  the metadata key
     * @param type the expected value type
     * @param <T>  the value type
     *
     * @return the value, or {@code null} if the key is absent
     *
     * @throws ClassCastException if the stored value is not of the expected type
     */
    public <T> T get(String key, Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object value = data.get(key);
        if (value == null) {
            return null;
        }

        if (!type.isInstance(value)) {
            throw new ClassCastException("Metadata value for key '" + key + "' is not of type " + type.getName());
        }

        return type.cast(value);
    }

    /** Builder for constructing {@link NbtFormatMetadata} instances. Null keys/values are silently ignored. */
    public static final class Builder {
        private final Map<String, Object> data = new HashMap<>();

        /**
         * Adds a metadata entry. Null or blank keys and null values are ignored.
         *
         * @param key   the metadata key
         * @param value the metadata value
         *
         * @return this builder
         */
        public Builder withValue(String key, Object value) {
            if (key == null || key.isBlank() || value == null) {
                return this;
            }
            data.put(key, value);
            return this;
        }

        public Builder merge(Map<String, Object> other) {
            if (other == null || other.isEmpty()) {
                return this;
            }

            other.forEach(this::withValue);
            return this;
        }

        public NbtFormatMetadata build() {
            if (data.isEmpty()) {
                return EMPTY;
            }

            return new NbtFormatMetadata(new HashMap<>(data));
        }
    }
}
