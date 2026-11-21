package net.aerh.imagegenerator.impl;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import feign.FeignException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.image.ImageUtil;
import net.aerh.imagegenerator.context.GenerationContext;
import net.aerh.imagegenerator.Generator;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.http.MojangApiClient;
import net.aerh.imagegenerator.http.MojangApiClientFactory;
import net.aerh.imagegenerator.http.MojangProfileResponse;
import net.aerh.imagegenerator.http.MojangSessionResponse;
import net.aerh.imagegenerator.item.GeneratedObject;
import net.aerh.imagegenerator.skull.RenderedPlayerSkull;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public class MinecraftPlayerHeadGenerator implements Generator {

    private static final Pattern TEXTURE_URL = Pattern.compile("(?:https?://textures.minecraft.net/texture/)?([a-zA-Z0-9]+)");
    private static final Pattern SKIN_BASE64_REGEX = Pattern.compile("^([A-Za-z0-9+/]{4})*([A-Za-z0-9+/]{3}=|[A-Za-z0-9+/]{2}==)?$");
    private static final Pattern HEX_TEXTURE_HASH = Pattern.compile("^[a-fA-F0-9]{64}$");
    private static final int MIN_SKIN_WIDTH = 64;
    private static final int MIN_SKIN_HEIGHT = 32;
    private static final Pattern TEXTURE_ID = Pattern.compile("^[a-zA-Z0-9]{1,128}$");
    private static final String TEXTURE_BASE_URL = "https://textures.minecraft.net/texture/";
    private static final int SKIN_FETCH_TIMEOUT_MS = 5_000;
    /** Largest skin download accepted. Real skins are a few kilobytes. */
    static final int MAX_SKIN_BYTES = 1024 * 1024;
    /** Largest skin size accepted, per side. Real skins are 64x64 or 64x32. */
    static final int MAX_SKIN_DIMENSION = 1024;
    /** The head render is 1250 px square, so upscaling is kept small; larger values are clamped. */
    public static final int MAX_UPSCALE = 4;
    /** The largest downscale divisor; larger values are clamped. */
    public static final int MAX_DOWNSCALE = 64;

    private final String textureId;
    private final int scale;
    private final MojangApiClient mojangApiClient;

    /**
     * Gets the Skin ID from a Base64 String
     *
     * @param base64SkinData Base64 Skin Data
     *
     * @return the skin ID
     */
    private static String base64ToSkinURL(String base64SkinData) {
        try {
            String json = new String(Base64.getDecoder().decode(base64SkinData), StandardCharsets.UTF_8);
            JsonObject skinData = JsonParser.parseString(json).getAsJsonObject();
            String url = skinData.getAsJsonObject("textures")
                .getAsJsonObject("SKIN")
                .get("url").getAsString();
            return url.substring(url.lastIndexOf('/') + 1);
        } catch (RuntimeException exception) {
            // Malformed base64, malformed JSON, or a missing/mistyped member
            throw new GeneratorException("Skin data is not a valid texture value");
        }
    }

    /**
     * Reads a downloaded skin, refusing anything that is not a plausibly sized image before it is
     * decoded. The size is taken from the image header, so an image claiming huge dimensions is
     * rejected without allocating its pixels.
     *
     * @return the decoded skin, or null if the bytes are not an image
     */
    static @Nullable BufferedImage decodeSkin(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (input == null || !readers.hasNext()) {
                return null;
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_SKIN_WIDTH || height < MIN_SKIN_HEIGHT || width > MAX_SKIN_DIMENSION || height > MAX_SKIN_DIMENSION) {
                    throw new GeneratorException("Skin has an unsupported size: %sx%s", String.valueOf(width), String.valueOf(height));
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    /** Reads at most {@link #MAX_SKIN_BYTES} from the stream, failing if there is more. */
    private static byte[] readSkinBytes(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_SKIN_BYTES + 1);
        if (bytes.length > MAX_SKIN_BYTES) {
            throw new GeneratorException("Skin download is larger than %s bytes", String.valueOf(MAX_SKIN_BYTES));
        }
        return bytes;
    }

    @Override
    public @NotNull GeneratedObject render(@Nullable GenerationContext generationContext) {
        log.debug("Rendering player head for texture '{}' (scale={})", textureId, scale);

        BufferedImage headImage = createHead(textureId);
        log.debug("Rendered player head image (dimensions {}x{})", headImage.getWidth(), headImage.getHeight());
        return new GeneratedObject(headImage);
    }

    private BufferedImage createHead(String textureId) {
        if (textureId == null || textureId.isBlank()) {
            throw new GeneratorException("No skin was provided for the player head");
        }

        if (isHexTextureHash(textureId)) {
            // Already a valid hex texture hash, use directly
        } else if (isSkinBase64(textureId)) {
            textureId = base64ToSkinURL(textureId);
        } else if (textureId.length() <= 16) {
            textureId = getPlayerHeadURL(textureId);
        } else {
            Matcher textureMatcher = TEXTURE_URL.matcher(textureId);
            if (textureMatcher.matches()) {
                textureId = textureMatcher.group(1);
            }
        }

        // Only a bare texture ID may reach the URL: anything else (slashes, dots, query strings)
        // would let the caller choose the path requested from the texture host.
        if (!TEXTURE_ID.matcher(textureId).matches()) {
            throw new GeneratorException("Invalid skin texture ID: `%s`", textureId);
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) URI.create(TEXTURE_BASE_URL + textureId).toURL().openConnection();
            connection.setConnectTimeout(SKIN_FETCH_TIMEOUT_MS);
            connection.setReadTimeout(SKIN_FETCH_TIMEOUT_MS);
            connection.setInstanceFollowRedirects(false);

            BufferedImage skin;
            try (InputStream stream = connection.getInputStream()) {
                skin = decodeSkin(readSkinBytes(stream));
            } finally {
                connection.disconnect();
            }

            if (skin == null) {
                throw new GeneratorException("Could not find skin with ID: `%s`", textureId);
            }

            BufferedImage head = new RenderedPlayerSkull(skin).generate().getImage();

            if (scale > 0) {
                head = ImageUtil.upscaleImage(head, Math.min(scale, MAX_UPSCALE));
            } else if (scale < 0) {
                // long arithmetic: Math.abs(Integer.MIN_VALUE) is still negative as an int
                head = ImageUtil.downscaleImage(head, (int) Math.min(Math.abs((long) scale), MAX_DOWNSCALE));
            }

            return head;
        } catch (MalformedURLException exception) {
            throw new GeneratorException("Malformed URL: `%s`", textureId);
        } catch (IOException exception) {
            throw new GeneratorException("Could not find skin with ID: `%s`", textureId);
        }
    }

    private String getPlayerHeadURL(String playerName) {
        playerName = playerName.replaceAll("[^a-zA-Z0-9_]", "");

        MojangProfileResponse profile;
        try {
            profile = mojangApiClient.lookupUuid(playerName);
        } catch (FeignException e) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (profile == null || profile.id() == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        MojangSessionResponse session;
        try {
            session = mojangApiClient.fetchProfile(profile.id());
        } catch (FeignException e) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        if (session == null || session.properties() == null || session.properties().isEmpty()) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        MojangSessionResponse.Property firstProperty = session.properties().get(0);
        if (firstProperty == null || firstProperty.value() == null) {
            throw new GeneratorException("Could not find player with name: `%s`", playerName);
        }

        return base64ToSkinURL(firstProperty.value());
    }

    private boolean isHexTextureHash(String string) {
        return string != null && HEX_TEXTURE_HASH.matcher(string).matches();
    }

    private boolean isSkinBase64(String string) {
        if (string == null || string.length() <= 16) {
            return false;
        }

        if (!SKIN_BASE64_REGEX.matcher(string).matches()) {
            return false;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(string);
        } catch (IllegalArgumentException e) {
            return false;
        }

        String json = new String(decoded, StandardCharsets.UTF_8);
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root != null && root.has("textures") && root.getAsJsonObject("textures").has("SKIN");
        } catch (JsonSyntaxException | IllegalStateException | ClassCastException ex) {
            return false;
        }
    }

    public static class Builder implements ClassBuilder<MinecraftPlayerHeadGenerator> {
        private String texture;
        private int scale;
        private MojangApiClient mojangApiClient;

        public Builder withSkin(String texture) {
            this.texture = texture;
            return this;
        }

        public Builder withScale(int scale) {
            this.scale = scale;
            return this;
        }

        public Builder withMojangApiClient(MojangApiClient mojangApiClient) {
            this.mojangApiClient = mojangApiClient;
            return this;
        }

        @Override
        public MinecraftPlayerHeadGenerator build() {
            MojangApiClient client = (mojangApiClient != null)
                ? mojangApiClient
                : MojangApiClientFactory.sharedInstance();
            return new MinecraftPlayerHeadGenerator(texture, scale, client);
        }
    }
}