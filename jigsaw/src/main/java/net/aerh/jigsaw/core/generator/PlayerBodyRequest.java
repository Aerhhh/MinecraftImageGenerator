package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.core.generator.body.ArmorMaterialRequest;
import net.aerh.jigsaw.core.generator.body.ArmorPiece;
import net.aerh.jigsaw.core.generator.body.ArmorSlot;
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
 * <p>Armor can be specified in two ways:
 * <ul>
 *   <li><b>Pre-loaded:</b> {@link #armorPieces()} - armor pieces with already-loaded textures</li>
 *   <li><b>By material:</b> {@link #armorMaterials()} - armor specified by material name,
 *       resolved at render time from the engine's resource pack</li>
 * </ul>
 *
 * @param base64Texture  optional Base64-encoded Minecraft profile texture JSON
 * @param textureUrl     optional direct URL to the skin image
 * @param playerName     optional player display name (informational only)
 * @param skinModel      the skin model type (classic or slim arms)
 * @param xRotation      rotation around the X axis (pitch) in radians
 * @param yRotation      rotation around the Y axis (yaw) in radians
 * @param zRotation      rotation around the Z axis (roll) in radians
 * @param armorPieces    pre-loaded armor pieces to render (may be empty)
 * @param armorMaterials armor pieces specified by material name, resolved at render time (may be empty)
 * @param scale          the scale factor to apply to the output; {@code 1} means no scaling
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
        List<ArmorMaterialRequest> armorMaterials,
        int scale
) implements RenderRequest {

    public PlayerBodyRequest {
        Objects.requireNonNull(base64Texture, "base64Texture must not be null");
        Objects.requireNonNull(textureUrl, "textureUrl must not be null");
        Objects.requireNonNull(playerName, "playerName must not be null");
        Objects.requireNonNull(skinModel, "skinModel must not be null");
        Objects.requireNonNull(armorPieces, "armorPieces must not be null");
        Objects.requireNonNull(armorMaterials, "armorMaterials must not be null");

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
        armorMaterials = List.copyOf(armorMaterials);
    }

    @Override
    public RenderRequest withInheritedScale(int scaleFactor) {
        if (this.scale != 1) {
            return this;
        }
        return new PlayerBodyRequest(base64Texture, textureUrl, playerName, skinModel,
                xRotation, yRotation, zRotation, armorPieces, armorMaterials, scaleFactor);
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
        private final List<ArmorMaterialRequest> armorMaterials = new ArrayList<>();
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

        /**
         * Adds a pre-loaded armor piece with an already-loaded texture image.
         */
        public Builder armorPiece(ArmorPiece piece) {
            this.armorPieces.add(Objects.requireNonNull(piece, "armorPiece must not be null"));
            return this;
        }

        /**
         * Adds multiple pre-loaded armor pieces.
         */
        public Builder armorPieces(List<ArmorPiece> pieces) {
            Objects.requireNonNull(pieces, "armorPieces must not be null");
            this.armorPieces.addAll(pieces);
            return this;
        }

        /**
         * Adds an armor piece by material name, to be resolved from the engine's resource pack.
         *
         * @param slot     the armor slot
         * @param material the material name (e.g. "iron", "diamond", "netherite")
         */
        public Builder armor(ArmorSlot slot, String material) {
            this.armorMaterials.add(ArmorMaterialRequest.of(slot, material));
            return this;
        }

        /**
         * Adds a dyed armor piece by material name (for leather armor).
         *
         * @param slot     the armor slot
         * @param material the material name (e.g. "leather")
         * @param dyeColor the dye color in ARGB format
         */
        public Builder armor(ArmorSlot slot, String material, int dyeColor) {
            this.armorMaterials.add(ArmorMaterialRequest.dyed(slot, material, dyeColor));
            return this;
        }

        public Builder scale(int val) {
            this.scale = val;
            return this;
        }

        public PlayerBodyRequest build() {
            return new PlayerBodyRequest(base64Texture, textureUrl, playerName, skinModel,
                    xRotation, yRotation, zRotation, armorPieces, armorMaterials, scale);
        }
    }
}
