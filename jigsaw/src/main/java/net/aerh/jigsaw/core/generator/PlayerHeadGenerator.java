package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.generator.skull.IsometricSkullRenderer;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import java.awt.image.BufferedImage;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;

/**
 * Renders an isometric 3D player head from a Minecraft skin.
 *
 * <p>The skin is loaded from either a Base64-encoded profile texture property or a direct URL,
 * then rendered as a 3D isometric head using {@link IsometricSkullRenderer}. The result includes
 * proper face shading, hat layer overlay, and anti-aliased downscaling.
 *
 * <p>If {@link PlayerHeadRequest#scale()} is greater than 1, the rendered head is further
 * upscaled by that factor using nearest-neighbor interpolation.
 *
 * <p>The HTTP client is injected via the constructor for testability. Use {@link #withDefaults()}
 * to create an instance with a default client configured for virtual threads.
 */
public final class PlayerHeadGenerator implements Generator<PlayerHeadRequest, GeneratorResult> {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final SkinLoader skinLoader;

    /**
     * Creates a new {@link PlayerHeadGenerator} with the given HTTP client.
     *
     * @param httpClient the HTTP client to use for skin fetching; must not be {@code null}
     */
    public PlayerHeadGenerator(HttpClient httpClient) {
        this.skinLoader = new SkinLoader(Objects.requireNonNull(httpClient, "httpClient must not be null"));
    }

    /**
     * Creates a new {@link PlayerHeadGenerator} with a default {@link HttpClient}
     * configured with a virtual thread executor and a 10-second connect timeout.
     *
     * @return a new generator with default HTTP settings
     */
    public static PlayerHeadGenerator withDefaults() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
        return new PlayerHeadGenerator(client);
    }

    @Override
    public GeneratorResult render(PlayerHeadRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage skin = loadSkin(input);
        BufferedImage head = IsometricSkullRenderer.render(skin);

        if (input.scale() > 1) {
            head = ImageUtil.upscaleImage(head, input.scale());
        }

        return new GeneratorResult.StaticImage(head);
    }

    @Override
    public Class<PlayerHeadRequest> inputType() {
        return PlayerHeadRequest.class;
    }

    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    private BufferedImage loadSkin(PlayerHeadRequest request) throws RenderException {
        return skinLoader.loadSkin(request.base64Texture(), request.textureUrl());
    }
}
