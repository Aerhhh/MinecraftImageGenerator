package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.generator.body.IsometricBodyRenderer;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * Renders an isometric 3D player body from a Minecraft skin.
 *
 * <p>The skin is loaded from either a Base64-encoded profile texture property or a direct URL,
 * then rendered as a full 3D isometric body using {@link IsometricBodyRenderer}. Supports
 * both classic and slim (Alex) skin models, arbitrary rotation angles, and optional armor rendering.
 *
 * <p>If {@link PlayerBodyRequest#scale()} is greater than 1, the rendered body is further
 * upscaled by that factor using nearest-neighbor interpolation.
 */
public final class PlayerBodyGenerator implements Generator<PlayerBodyRequest, GeneratorResult> {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SkinLoader skinLoader;

    /**
     * Creates a new {@link PlayerBodyGenerator} with the given HTTP client.
     *
     * @param httpClient the HTTP client to use for skin fetching; must not be {@code null}
     */
    public PlayerBodyGenerator(HttpClient httpClient) {
        this.skinLoader = new SkinLoader(Objects.requireNonNull(httpClient, "httpClient must not be null"));
    }

    /**
     * Creates a new {@link PlayerBodyGenerator} with a default {@link HttpClient}
     * configured with a virtual thread executor and a 10-second connect timeout.
     *
     * @return a new generator with default HTTP settings
     */
    public static PlayerBodyGenerator withDefaults() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        return new PlayerBodyGenerator(client);
    }

    @Override
    public GeneratorResult render(PlayerBodyRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage skin = skinLoader.loadSkin(input.base64Texture(), input.textureUrl());
        BufferedImage body = IsometricBodyRenderer.render(
                skin, input.skinModel(),
                input.xRotation(), input.yRotation(), input.zRotation(),
                input.armorPieces());

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
}
