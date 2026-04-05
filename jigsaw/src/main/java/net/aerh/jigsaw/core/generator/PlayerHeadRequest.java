package net.aerh.jigsaw.core.generator;

import java.util.Objects;
import java.util.Optional;

/**
 * Input request for the {@link PlayerHeadGenerator}.
 *
 * <p>Exactly one of {@code base64Texture} or {@code textureUrl} should be provided.
 * If both are set, {@code base64Texture} takes priority.
 *
 * @param base64Texture optional Base64-encoded Minecraft profile texture JSON (the value inside
 *                      {@code textures.SKIN.url} after decoding the profile property)
 * @param textureUrl    optional direct URL to the skin image
 * @param playerName    optional player display name (informational only, not used for rendering)
 * @param scale         the scale factor to apply to the extracted face; {@code 1} means no scaling
 */
public record PlayerHeadRequest(
        Optional<String> base64Texture,
        Optional<String> textureUrl,
        Optional<String> playerName,
        int scale
) {

    public PlayerHeadRequest {
        Objects.requireNonNull(base64Texture, "base64Texture must not be null");
        Objects.requireNonNull(textureUrl, "textureUrl must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");

        if (base64Texture.isEmpty() && textureUrl.isEmpty()) {
            throw new IllegalArgumentException("At least one of base64Texture or textureUrl must be present");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got: " + scale);
        }
        if (scale > 64) {
            throw new IllegalArgumentException("scale must be <= 64, got: " + scale);
        }
    }

    /**
     * Returns a builder for constructing a {@link PlayerHeadRequest} from a Base64 texture value.
     */
    public static Builder fromBase64(String base64Texture) {
        return new Builder().base64Texture(base64Texture);
    }

    /**
     * Returns a builder for constructing a {@link PlayerHeadRequest} from a texture URL.
     */
    public static Builder fromUrl(String textureUrl) {
        return new Builder().textureUrl(textureUrl);
    }

    /**
     * Builder for {@link PlayerHeadRequest}.
     */
    public static final class Builder {

        private Optional<String> base64Texture = Optional.empty();
        private Optional<String> textureUrl = Optional.empty();
        private Optional<String> playerName = Optional.empty();
        private int scale = 1;

        private Builder() {}

        /**
         * Sets the Base64-encoded Minecraft profile texture value.
         *
         * @param val the Base64 texture string; must not be {@code null}
         *
         * @return this builder for chaining
         */
        public Builder base64Texture(String val) {
            this.base64Texture = Optional.of(Objects.requireNonNull(val, "base64Texture must not be null"));
            return this;
        }

        /**
         * Sets the direct URL to the skin image.
         *
         * @param val the skin URL; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder textureUrl(String val) {
            this.textureUrl = Optional.of(Objects.requireNonNull(val, "textureUrl must not be null"));
            return this;
        }

        /**
         * Sets an optional player display name (informational only, not used for rendering).
         *
         * @param val the player name; must not be {@code null}
         * @return this builder for chaining
         */
        public Builder playerName(String val) {
            this.playerName = Optional.of(Objects.requireNonNull(val, "playerName must not be null"));
            return this;
        }

        /**
         * Sets the scale factor applied to the extracted 8x8 face image.
         *
         * @param val the scale factor; must be {@code >= 1}
         * @return this builder for chaining
         */
        public Builder scale(int val) {
            this.scale = val;
            return this;
        }

        /**
         * Builds the {@link PlayerHeadRequest}.
         *
         * @return a new request
         * @throws IllegalArgumentException if neither {@code base64Texture} nor {@code textureUrl} is set
         */
        public PlayerHeadRequest build() {
            return new PlayerHeadRequest(base64Texture, textureUrl, playerName, scale);
        }
    }
}
