package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.generator.skull.IsometricSkullRenderer;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders an isometric 3D player head from a Minecraft skin.
 *
 * <p>The skin is loaded from either a Base64-encoded profile texture property or a direct URL,
 * then rendered as a 3D isometric head using {@link IsometricSkullRenderer}. The result includes
 * proper face shading, hat layer overlay, and anti-aliased downscaling.
 *
 * <p>If {@link PlayerHeadRequest#scale()} is greater than 1, the rendered head is further
 * upscaled by that factor using nearest-neighbor interpolation.
 */
public final class PlayerHeadGenerator implements Generator<PlayerHeadRequest, GeneratorResult> {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

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

    // -------------------------------------------------------------------------
    // Skin loading
    // -------------------------------------------------------------------------

    private static BufferedImage loadSkin(PlayerHeadRequest request) throws RenderException {
        if (request.base64Texture().isPresent()) {
            return loadFromBase64(request.base64Texture().get());
        }
        return loadFromUrl(request.textureUrl().get());
    }

    private static BufferedImage loadFromBase64(String base64Texture) throws RenderException {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Texture);
            String json = new String(decoded, java.nio.charset.StandardCharsets.UTF_8);

            Matcher urlMatcher = URL_PATTERN.matcher(json);
            if (!urlMatcher.find()) {
                throw new RenderException(
                        "Could not find texture URL in decoded Base64 profile texture",
                        Map.of("json", json)
                );
            }
            String skinUrl = urlMatcher.group(1);
            return loadFromUrl(skinUrl);
        } catch (IllegalArgumentException e) {
            throw new RenderException(
                    "Invalid Base64 texture string",
                    Map.of("error", e.getMessage()),
                    e
            );
        }
    }

    private static BufferedImage loadFromUrl(String skinUrl) throws RenderException {
        try {
            URL url = URI.create(skinUrl).toURL();
            try (InputStream in = url.openStream()) {
                BufferedImage skin = ImageIO.read(in);
                if (skin == null) {
                    throw new RenderException(
                            "Failed to decode skin image from URL (ImageIO returned null)",
                            Map.of("url", skinUrl)
                    );
                }
                return skin;
            }
        } catch (IOException e) {
            throw new RenderException(
                    "Failed to load skin from URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        } catch (IllegalArgumentException e) {
            throw new RenderException(
                    "Invalid skin URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        }
    }
}
