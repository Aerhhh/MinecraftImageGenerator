package net.aerh.jigsaw.core.generator.player;

import net.aerh.jigsaw.api.generator.RenderRequest;

import java.util.Objects;
import java.util.Optional;

/**
 * Request to render a full isometric player model.
 *
 * <p>Exactly one of {@code base64Texture} or {@code textureUrl} must be provided.
 * If both are set, {@code base64Texture} takes priority.
 *
 * @param base64Texture optional Base64-encoded Minecraft profile texture JSON
 * @param textureUrl    optional direct URL to the skin image
 * @param playerName    optional player display name (informational only)
 * @param armor         optional armor set to render on the model
 * @param slim          true for slim (Alex) arms, false for classic (Steve)
 * @param scale         output scale factor; {@code 1} means no upscaling
 */
public record PlayerModelRequest(
        Optional<String> base64Texture,
        Optional<String> textureUrl,
        Optional<String> playerName,
        Optional<ArmorSet> armor,
        boolean slim,
        int scale
) implements RenderRequest {

    public PlayerModelRequest {
        Objects.requireNonNull(base64Texture, "base64Texture must not be null");
        Objects.requireNonNull(textureUrl, "textureUrl must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        Objects.requireNonNull(armor, "armor must not be null");

        if (base64Texture.isEmpty() && textureUrl.isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one of base64Texture or textureUrl must be present");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got: " + scale);
        }
        if (scale > 64) {
            throw new IllegalArgumentException("scale must be <= 64, got: " + scale);
        }
    }

    @Override
    public RenderRequest withInheritedScale(int scaleFactor) {
        if (this.scale != 1) {
            return this;
        }
        return new PlayerModelRequest(base64Texture, textureUrl, playerName, armor, slim, scaleFactor);
    }

    /**
     * Returns a builder initialized with a Base64-encoded texture.
     */
    public static Builder fromBase64(String base64Texture) {
        return new Builder().base64Texture(base64Texture);
    }

    /**
     * Returns a builder initialized with a texture URL.
     */
    public static Builder fromUrl(String textureUrl) {
        return new Builder().textureUrl(textureUrl);
    }

    /**
     * Builder for {@link PlayerModelRequest}.
     */
    public static final class Builder {
        private Optional<String> base64Texture = Optional.empty();
        private Optional<String> textureUrl = Optional.empty();
        private Optional<String> playerName = Optional.empty();
        private Optional<ArmorSet> armor = Optional.empty();
        private boolean slim = false;
        private int scale = 1;

        private Builder() {}

        public Builder base64Texture(String val) {
            this.base64Texture = Optional.of(Objects.requireNonNull(val));
            return this;
        }

        public Builder textureUrl(String val) {
            this.textureUrl = Optional.of(Objects.requireNonNull(val));
            return this;
        }

        public Builder playerName(String val) {
            this.playerName = Optional.of(Objects.requireNonNull(val));
            return this;
        }

        public Builder armor(ArmorSet val) {
            this.armor = Optional.of(Objects.requireNonNull(val));
            return this;
        }

        public Builder slim(boolean val) {
            this.slim = val;
            return this;
        }

        public Builder scale(int val) {
            this.scale = val;
            return this;
        }

        public PlayerModelRequest build() {
            return new PlayerModelRequest(
                    base64Texture, textureUrl, playerName, armor, slim, scale);
        }
    }
}
