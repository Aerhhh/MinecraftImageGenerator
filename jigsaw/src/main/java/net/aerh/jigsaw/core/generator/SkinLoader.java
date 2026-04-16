package net.aerh.jigsaw.core.generator;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.aerh.jigsaw.exception.RenderException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared utility for loading Minecraft skin images from Base64-encoded profile texture
 * properties or direct URLs.
 *
 * <p>Used by both {@link PlayerHeadGenerator} and
 * {@link PlayerBodyGenerator} to avoid duplicating the skin-loading logic.
 */
public final class SkinLoader {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final long SKIN_CACHE_MAX_SIZE = 256;
    private static final Duration SKIN_CACHE_TTL = Duration.ofMinutes(10);

    private final HttpClient httpClient;
    private final Cache<String, BufferedImage> skinCache;

    /**
     * Creates a new skin loader with the given HTTP client.
     *
     * @param httpClient the HTTP client to use for skin fetching; must not be {@code null}
     */
    public SkinLoader(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.skinCache = Caffeine.newBuilder()
                .maximumSize(SKIN_CACHE_MAX_SIZE)
                .expireAfterWrite(SKIN_CACHE_TTL)
                .build();
    }

    /**
     * Loads a skin image from either a Base64-encoded texture property or a direct URL.
     * If both are present, Base64 takes priority.
     *
     * @param base64Texture optional Base64-encoded Minecraft profile texture JSON
     * @param textureUrl    optional direct URL to the skin image
     * @return the loaded skin image
     * @throws RenderException if loading fails
     */
    public BufferedImage loadSkin(Optional<String> base64Texture, Optional<String> textureUrl) throws RenderException {
        if (base64Texture.isPresent()) {
            return loadFromBase64(base64Texture.get());
        }
        return loadFromUrl(textureUrl.get());
    }

    /**
     * Decodes a Base64 profile texture JSON, extracts the skin URL, and downloads the skin.
     *
     * @param base64Texture the Base64-encoded texture JSON
     * @return the loaded skin image
     * @throws RenderException if decoding, URL extraction, or download fails
     */
    public BufferedImage loadFromBase64(String base64Texture) throws RenderException {
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

    /**
     * Downloads a skin image from a direct URL.
     *
     * @param skinUrl the skin image URL
     * @return the loaded skin image
     * @throws RenderException if the download or image decoding fails
     */
    public BufferedImage loadFromUrl(String skinUrl) throws RenderException {
        BufferedImage cached = skinCache.getIfPresent(skinUrl);
        if (cached != null) {
            return cached;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(skinUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                throw new RenderException(
                        "Failed to fetch skin: HTTP " + response.statusCode(),
                        Map.of("url", skinUrl, "statusCode", String.valueOf(response.statusCode()))
                );
            }

            try (InputStream in = response.body()) {
                BufferedImage skin = ImageIO.read(in);
                if (skin == null) {
                    throw new RenderException(
                            "Failed to decode skin image from URL (ImageIO returned null)",
                            Map.of("url", skinUrl, "statusCode", String.valueOf(response.statusCode()))
                    );
                }
                skinCache.put(skinUrl, skin);
                return skin;
            }
        } catch (IOException e) {
            throw new RenderException(
                    "Failed to load skin from URL: " + skinUrl,
                    Map.of("url", skinUrl),
                    e
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RenderException(
                    "Skin fetch interrupted for URL: " + skinUrl,
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
