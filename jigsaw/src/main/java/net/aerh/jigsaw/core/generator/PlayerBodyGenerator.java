package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.generator.body.ArmorMaterialRequest;
import net.aerh.jigsaw.core.generator.body.ArmorPiece;
import net.aerh.jigsaw.core.generator.body.ArmorTextureProvider;
import net.aerh.jigsaw.core.generator.body.IsometricBodyRenderer;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;

/**
 * Renders an isometric 3D player body from a Minecraft skin.
 *
 * <p>The skin is loaded from either a Base64-encoded profile texture property or a direct URL,
 * then rendered as a full 3D isometric body using {@link IsometricBodyRenderer}. Supports
 * both classic and slim (Alex) skin models, arbitrary rotation angles, and optional armor rendering.
 *
 * <p>Armor can be provided as pre-loaded {@link ArmorPiece} images or as material names
 * (e.g. "iron", "diamond") that are resolved at render time from the configured
 * {@link ArmorTextureProvider}.
 */
public final class PlayerBodyGenerator implements Generator<PlayerBodyRequest, GeneratorResult> {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SkinLoader skinLoader;
    private final ArmorTextureProvider armorTextureProvider;

    /**
     * Creates a new {@link PlayerBodyGenerator} with the given HTTP client and optional
     * armor texture provider.
     *
     * @param httpClient           the HTTP client for skin fetching; must not be {@code null}
     * @param armorTextureProvider the armor texture provider for resolving material names;
     *                             may be {@code null} if material-based armor is not needed
     */
    public PlayerBodyGenerator(HttpClient httpClient, ArmorTextureProvider armorTextureProvider) {
        this.skinLoader = new SkinLoader(Objects.requireNonNull(httpClient, "httpClient must not be null"));
        this.armorTextureProvider = armorTextureProvider;
    }

    /**
     * Creates a new {@link PlayerBodyGenerator} with a default {@link HttpClient}
     * and no armor texture provider.
     *
     * @return a new generator with default HTTP settings
     */
    public static PlayerBodyGenerator withDefaults() {
        return withDefaults(null);
    }

    /**
     * Creates a new {@link PlayerBodyGenerator} with a default {@link HttpClient}
     * and the given armor texture provider.
     *
     * @param armorTextureProvider the armor texture provider; may be {@code null}
     * @return a new generator with default HTTP settings
     */
    public static PlayerBodyGenerator withDefaults(ArmorTextureProvider armorTextureProvider) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        return new PlayerBodyGenerator(client, armorTextureProvider);
    }

    @Override
    public GeneratorResult render(PlayerBodyRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage skin = skinLoader.loadSkin(input.base64Texture(), input.textureUrl());

        List<ArmorPiece> allArmor = resolveArmor(input);

        BufferedImage body = IsometricBodyRenderer.render(
                skin, input.skinModel(),
                input.xRotation(), input.yRotation(), input.zRotation(),
                allArmor);

        if (input.scale() > 1) {
            body = ImageUtil.upscaleImage(body, input.scale());
        }

        return new GeneratorResult.StaticImage(body);
    }

    @Override
    public Class<PlayerBodyRequest> inputType() {
        return PlayerBodyRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    /**
     * Merges pre-loaded armor pieces with material-based armor requests, resolving
     * material names via the armor texture provider.
     */
    private List<ArmorPiece> resolveArmor(PlayerBodyRequest input) throws RenderException {
        List<ArmorMaterialRequest> materials = input.armorMaterials();
        if (materials.isEmpty()) {
            return input.armorPieces();
        }

        if (armorTextureProvider == null) {
            throw new RenderException(
                    "Armor specified by material name but no resource pack is configured. "
                            + "Use Engine.builder().resourcePack() to provide one, or use pre-loaded ArmorPiece images.",
                    Map.of("materials", materials.toString())
            );
        }

        List<ArmorPiece> merged = new ArrayList<>(input.armorPieces());
        for (ArmorMaterialRequest req : materials) {
            Optional<ArmorPiece> piece = req.dyeColor().isPresent()
                    ? armorTextureProvider.piece(req.slot(), req.material(), req.dyeColor().get())
                    : armorTextureProvider.piece(req.slot(), req.material());

            if (piece.isEmpty()) {
                throw new RenderException(
                        "Armor texture not found for material: " + req.material()
                                + " (slot: " + req.slot() + ")",
                        Map.of("material", req.material(), "slot", req.slot().name())
                );
            }
            merged.add(piece.get());
        }
        return merged;
    }
}
