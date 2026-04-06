package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.core.util.GraphicsUtil;
import net.aerh.jigsaw.exception.RenderException;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Renders a player head by extracting the 8x8 face region from a Minecraft skin.
 *
 * <p>The skin layout follows Minecraft's standard 64x64 texture:
 * <ul>
 *   <li>Face layer: x=8, y=8, 8x8 pixels</li>
 *   <li>Hat layer: x=40, y=8, 8x8 pixels (blended on top)</li>
 * </ul>
 *
 * <p>Skins can be sourced from a Base64-encoded Minecraft profile texture property or
 * from a direct URL. If {@link PlayerHeadRequest#scale()} is greater than 1 the resulting
 * 8x8 face image is upscaled by that factor using nearest-neighbor interpolation.
 */
public final class PlayerHeadGenerator implements Generator<PlayerHeadRequest, GeneratorResult> {

    // Face layer position in a standard Minecraft skin
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;

    // Hat overlay layer position
    private static final int HAT_X = 40;
    private static final int HAT_Y = 8;

    private static final int FACE_SIZE = 8;

    /**
     * Loads the skin, composites the face and hat layers, and returns the result.
     *
     * @param input   the player head request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     *
     * @return a static image of the player's face
     *
     * @throws RenderException if the skin cannot be loaded or decoded
     */
    @Override
    public GeneratorResult render(PlayerHeadRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        BufferedImage skin = loadSkin(input);
        BufferedImage face = compositeFace(skin);

        if (input.scale() > 1) {
            face = ImageUtil.upscaleImage(face, input.scale());
        }

        return new GeneratorResult.StaticImage(face);
    }

    /**
     * Returns the input type accepted by this generator.
     *
     * @return {@link PlayerHeadRequest}
     */
    @Override
    public Class<PlayerHeadRequest> inputType() {
        return PlayerHeadRequest.class;
    }

    /**
     * Returns the output type produced by this generator.
     *
     * @return {@link GeneratorResult}
     */
    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }

    /**
     * Loads the skin image from either a Base64 texture or a URL.
     */
    private static BufferedImage loadSkin(PlayerHeadRequest request) throws RenderException {
        if (request.base64Texture().isPresent()) {
            return loadFromBase64(request.base64Texture().get());
        }
        return loadFromUrl(request.textureUrl().get());
    }

    /**
     * Decodes a Base64 Minecraft profile texture property and fetches the skin URL from it.
     *
     * <p>The Base64 string decodes to JSON such as:
     * <pre>{"textures":{"SKIN":{"url":"http://textures.minecraft.net/..."},...}}</pre>
     */
    private static BufferedImage loadFromBase64(String base64Texture) throws RenderException {
        try {
            byte[] decoded = Base64.getDecoder().decode(base64Texture);
            String json = new String(decoded, StandardCharsets.UTF_8);

            // Extract the URL from the JSON using a simple string search to avoid a Gson dependency
            // in this class. Pattern: "url":"<url>"
            String urlMarker = "\"url\":\"";
            int urlStart = json.indexOf(urlMarker);
            if (urlStart < 0) {
                throw new RenderException(
                        "Could not find texture URL in decoded Base64 profile texture",
                        Map.of("json", json)
                );
            }
            urlStart += urlMarker.length();
            int urlEnd = json.indexOf('"', urlStart);
            if (urlEnd < 0) {
                throw new RenderException(
                        "Malformed texture URL in decoded Base64 profile texture",
                        Map.of("json", json)
                );
            }

            String skinUrl = json.substring(urlStart, urlEnd);
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

    /**
     * Extracts the face from the skin and composites the hat layer on top.
     */
    private static BufferedImage compositeFace(BufferedImage skin) {
        // Extract the face base layer
        BufferedImage face = new BufferedImage(FACE_SIZE, FACE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = face.createGraphics();
        GraphicsUtil.disableAntialiasing(g);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        // Draw face layer
        g.drawImage(skin, 0, 0, FACE_SIZE, FACE_SIZE,
                FACE_X, FACE_Y, FACE_X + FACE_SIZE, FACE_Y + FACE_SIZE,
                null);

        // Composite hat layer on top (hat pixels with alpha 0 are transparent so they don't occlude)
        g.drawImage(skin, 0, 0, FACE_SIZE, FACE_SIZE,
                HAT_X, HAT_Y, HAT_X + FACE_SIZE, HAT_Y + FACE_SIZE,
                null);

        g.dispose();
        return face;
    }
}
