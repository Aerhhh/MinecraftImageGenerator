package net.aerh.jigsaw.core.font;

import net.aerh.jigsaw.api.font.FontProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Optional;

/**
 * A {@link FontProvider} that loads an AWT {@link Font} from a classpath resource.
 *
 * <p>If the resource is not found, the provider falls back to a monospaced AWT font so that
 * rendering can still proceed without hard-failing.
 */
public class ResourceFontProvider implements FontProvider {

    private static final Logger log = LoggerFactory.getLogger(ResourceFontProvider.class);
    private static final float DEFAULT_FONT_SIZE = 16f;

    private final String fontId;
    private final Font font;
    private final FontMetrics metrics;

    /**
     * @param fontId       unique identifier (e.g. {@code "minecraft:default"})
     * @param resourcePath classpath path to the font file (e.g. {@code "minecraft/assets/fonts/unifont-17.0.03.otf"})
     */
    public ResourceFontProvider(String fontId, String resourcePath) {
        Objects.requireNonNull(fontId, "fontId must not be null");
        Objects.requireNonNull(resourcePath, "resourcePath must not be null");

        this.fontId = fontId;
        this.font = loadFont(resourcePath);
        this.metrics = buildMetrics(this.font);
    }

    private static Font loadFont(String resourcePath) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        try (InputStream is = cl.getResourceAsStream(resourcePath)) {
            if (is == null) {
                log.warn("Font resource not found: {}; falling back to monospaced font", resourcePath);
                return new Font(Font.MONOSPACED, Font.PLAIN, (int) DEFAULT_FONT_SIZE);
            }
            Font loaded = Font.createFont(Font.TRUETYPE_FONT, is).deriveFont(DEFAULT_FONT_SIZE);
            log.debug("Loaded font from: {}", resourcePath);
            return loaded;
        } catch (Exception e) {
            log.warn("Failed to load font from: {}; falling back to monospaced font", resourcePath, e);
            return new Font(Font.MONOSPACED, Font.PLAIN, (int) DEFAULT_FONT_SIZE);
        }
    }

    private static FontMetrics buildMetrics(Font font) {
        BufferedImage scratch = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = scratch.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        g.dispose();
        return fm;
    }

    /**
     * Returns the unique identifier for this font provider.
     *
     * @return the font ID (e.g. {@code "minecraft:default"})
     */
    @Override
    public String id() {
        return fontId;
    }

    /**
     * Returns the loaded AWT {@link Font}.
     *
     * @return an {@link java.util.Optional} containing the font; never empty
     */
    @Override
    public Optional<Font> getFont() {
        return Optional.of(font);
    }

    /**
     * Returns the pixel width of the given character in this font.
     *
     * @param c the character to measure
     *
     * @return the character width in pixels
     */
    @Override
    public int getCharWidth(char c) {
        return metrics.charWidth(c);
    }

    /**
     * Returns {@code true} if this font can display the given character.
     *
     * @param c the character to test
     * @return whether the font supports the character
     */
    @Override
    public boolean supportsChar(char c) {
        return font.canDisplay(c);
    }
}
