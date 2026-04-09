package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.core.generator.body.ArmorPiece;
import net.aerh.jigsaw.core.generator.body.PlayerBodySettings;
import net.aerh.jigsaw.core.generator.body.SkinModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Input request for the {@link PlayerBodyGenerator}.
 *
 * <p>Exactly one of {@code base64Texture} or {@code textureUrl} should be provided.
 * If both are set, {@code base64Texture} takes priority.
 *
 * @param base64Texture optional Base64-encoded Minecraft profile texture JSON
 * @param textureUrl    optional direct URL to the skin image
 * @param playerName    optional player display name (informational only)
 * @param skinModel     the skin model type (classic or slim arms)
 * @param xRotation     rotation around the X axis (pitch) in radians
 * @param yRotation     rotation around the Y axis (yaw) in radians
 * @param zRotation     rotation around the Z axis (roll) in radians
 * @param armorPieces   armor pieces to render on the body (may be empty)
 * @param scale         the scale factor to apply to the output; {@code 1} means no scaling
 */
public record PlayerBodyRequest(
        Optional<String> base64Texture,
        Optional<String> textureUrl,
        Optional<String> playerName,
        SkinModel skinModel,
        double xRotation,
        double yRotation,
        double zRotation,
        List<ArmorPiece> armorPieces,
        int scale
) implements RenderRequest {

    public PlayerBodyRequest {
        Objects.requireNonNull(base64Texture, "base64Texture must not be null");
        Objects.requireNonNull(textureUrl, "textureUrl must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        Objects.requireNonNull(skinModel, "skinModel must not be null");
        Objects.requireNonNull(armorPieces, "armorPieces must not be null");

        if (base64Texture.isEmpty() && textureUrl.isEmpty()) {
            throw new IllegalArgumentException("At least one of base64Texture or textureUrl must be present");
        }
        if (scale < 1) {
            throw new IllegalArgumentException("scale must be >= 1, got: " + scale);
        }
        if (scale > 64) {
            throw new IllegalArgumentException("scale must be <= 64, got: " + scale);
        }

        armorPieces = List.copyOf(armorPieces);
    }

    @Override
    public RenderRequest withInheritedScale(int scaleFactor) {
        if (this.scale != 1) {
            return this;
        }
        return new PlayerBodyRequest(base64Texture, textureUrl, playerName, skinModel,
                xRotation, yRotation, zRotation, armorPieces, scaleFactor);
    }

    /**
     * Returns a builder for constructing a {@link PlayerBodyRequest} from a Base64 texture value.
     */
    public static Builder fromBase64(String base64Texture) {
        return new Builder().base64Texture(base64Texture);
    }

    /**
     * Returns a builder for constructing a {@link PlayerBodyRequest} from a texture URL.
     */
    public static Builder fromUrl(String textureUrl) {
        return new Builder().textureUrl(textureUrl);
    }

    /**
     * Builder for {@link PlayerBodyRequest}.
     */
    public static final class Builder {

        private Optional<String> base64Texture = Optional.empty();
        private Optional<String> textureUrl = Optional.empty();
        private Optional<String> playerName = Optional.empty();
        private SkinModel skinModel = SkinModel.CLASSIC;
        private double xRotation = PlayerBodySettings.DEFAULT_X_ROTATION;
        private double yRotation = PlayerBodySettings.DEFAULT_Y_ROTATION;
        private double zRotation = PlayerBodySettings.DEFAULT_Z_ROTATION;
        private final List<ArmorPiece> armorPieces = new ArrayList<>();
        private int scale = 1;

        private Builder() {}

        public Builder base64Texture(String val) {
            this.base64Texture = Optional.of(Objects.requireNonNull(val, "base64Texture must not be null"));
            return this;
        }

        public Builder textureUrl(String val) {
            this.textureUrl = Optional.of(Objects.requireNonNull(val, "textureUrl must not be null"));
            return this;
        }

        public Builder playerName(String val) {
            this.playerName = Optional.of(Objects.requireNonNull(val, "playerName must not be null"));
            return this;
        }

        public Builder skinModel(SkinModel val) {
            this.skinModel = Objects.requireNonNull(val, "skinModel must not be null");
            return this;
        }

        public Builder xRotation(double val) {
            this.xRotation = val;
            return this;
        }

        public Builder yRotation(double val) {
            this.yRotation = val;
            return this;
        }

        public Builder zRotation(double val) {
            this.zRotation = val;
            return this;
        }

        public Builder armorPiece(ArmorPiece piece) {
            this.armorPieces.add(Objects.requireNonNull(piece, "armorPiece must not be null"));
            return this;
        }

        public Builder armorPieces(List<ArmorPiece> pieces) {
            Objects.requireNonNull(pieces, "armorPieces must not be null");
            this.armorPieces.addAll(pieces);
            return this;
        }

        public Builder scale(int val) {
            this.scale = val;
            return this;
        }

        public PlayerBodyRequest build() {
            return new PlayerBodyRequest(base64Texture, textureUrl, playerName, skinModel,
                    xRotation, yRotation, zRotation, armorPieces, scale);
        }
    }
}
