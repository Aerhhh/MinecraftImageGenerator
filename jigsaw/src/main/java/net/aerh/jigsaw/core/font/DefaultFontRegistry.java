package net.aerh.jigsaw.core.font;

import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.exception.RegistryException;

import java.awt.Font;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link FontRegistry} that is pre-populated with the built-in Minecraft font providers.
 *
 * <p>Use {@link #withBuiltins()} to obtain an instance with all standard fonts registered.
 *
 * <h3>Registered font IDs</h3>
 * <ul>
 *   <li>{@link MinecraftFontId#DEFAULT} - Minecraft-Regular.otf</li>
 *   <li>{@link MinecraftFontId#DEFAULT_BOLD} - Minecraft-Bold.otf</li>
 *   <li>{@link MinecraftFontId#DEFAULT_ITALIC} - Minecraft-Italic.otf</li>
 *   <li>{@link MinecraftFontId#DEFAULT_BOLD_ITALIC} - Minecraft-BoldItalic.otf</li>
 *   <li>{@link MinecraftFontId#GALACTIC} - Minecraft-Galactic.otf</li>
 *   <li>{@link MinecraftFontId#ILLAGERALT} - Minecraft-Illageralt.otf</li>
 *   <li>{@link MinecraftFontId#UNIFONT} - unifont-17.0.03.otf (fallback)</li>
 *   <li>{@link MinecraftFontId#UNIFONT_UPPER} - unifont_upper-17.0.03.otf (fallback for > U+FFFF)</li>
 * </ul>
 *
 * <h3>Character resolution fallback chain</h3>
 * <ol>
 *   <li>The provider registered under the requested font ID.</li>
 *   <li>For {@link #resolveForChar(char)}: any registered provider that declares support via
 *       {@link FontProvider#supportsChar(char)}.</li>
 *   <li>The {@link MinecraftFontId#DEFAULT} provider as the ultimate fallback.</li>
 * </ol>
 */
public class DefaultFontRegistry implements FontRegistry {

    private static final String FONT_PATH_REGULAR      = "minecraft/assets/fonts/Minecraft-Regular.otf";
    private static final String FONT_PATH_BOLD         = "minecraft/assets/fonts/Minecraft-Bold.otf";
    private static final String FONT_PATH_ITALIC       = "minecraft/assets/fonts/Minecraft-Italic.otf";
    private static final String FONT_PATH_BOLD_ITALIC  = "minecraft/assets/fonts/Minecraft-BoldItalic.otf";
    private static final String FONT_PATH_GALACTIC     = "minecraft/assets/fonts/Minecraft-Galactic.otf";
    private static final String FONT_PATH_ILLAGERALT   = "minecraft/assets/fonts/Minecraft-Illageralt.otf";
    private static final String FONT_PATH_UNIFONT      = "minecraft/assets/fonts/unifont-17.0.03.otf";
    private static final String FONT_PATH_UNIFONT_UPPER = "minecraft/assets/fonts/unifont_upper-17.0.03.otf";

    private final ConcurrentHashMap<String, FontProvider> providers = new ConcurrentHashMap<>();

    private DefaultFontRegistry() {}

    /**
     * Creates a {@link DefaultFontRegistry} with all standard Minecraft font providers
     * pre-registered at the default size of {@link ResourceFontProvider#DEFAULT_FONT_SIZE}.
     */
    public static DefaultFontRegistry withBuiltins() {
        DefaultFontRegistry registry = new DefaultFontRegistry();
        registry.register(new ResourceFontProvider(MinecraftFontId.DEFAULT,           FONT_PATH_REGULAR));
        registry.register(new ResourceFontProvider(MinecraftFontId.DEFAULT_BOLD,      FONT_PATH_BOLD));
        registry.register(new ResourceFontProvider(MinecraftFontId.DEFAULT_ITALIC,    FONT_PATH_ITALIC));
        registry.register(new ResourceFontProvider(MinecraftFontId.DEFAULT_BOLD_ITALIC, FONT_PATH_BOLD_ITALIC));
        registry.register(new ResourceFontProvider(MinecraftFontId.GALACTIC,          FONT_PATH_GALACTIC));
        registry.register(new ResourceFontProvider(MinecraftFontId.ILLAGERALT,        FONT_PATH_ILLAGERALT));
        registry.register(new ResourceFontProvider(MinecraftFontId.UNIFONT,           FONT_PATH_UNIFONT));
        registry.register(new ResourceFontProvider(MinecraftFontId.UNIFONT_UPPER,     FONT_PATH_UNIFONT_UPPER));
        return registry;
    }

    /**
     * Returns the provider registered under the given font ID.
     *
     * @param fontId the font identifier to look up; must not be {@code null}
     *
     * @return the registered {@link FontProvider}
     *
     * @throws net.aerh.jigsaw.exception.RegistryException if no provider is registered for the ID
     */
    @Override
    public FontProvider resolve(String fontId) {
        Objects.requireNonNull(fontId, "fontId must not be null");
        FontProvider provider = providers.get(fontId);
        if (provider == null) {
            throw new RegistryException("No FontProvider registered for font ID: " + fontId,
                    Map.of("fontId", fontId));
        }
        return provider;
    }

    /**
     * Returns a font provider that supports the given character, falling back to the default font.
     *
     * @param c the character to find a provider for
     * @return a suitable {@link FontProvider}
     * @throws net.aerh.jigsaw.exception.RegistryException if no provider is registered at all
     */
    @Override
    public FontProvider resolveForChar(char c) {
        // First pass: find a provider that explicitly supports the character
        for (FontProvider provider : providers.values()) {
            if (provider.supportsChar(c)) {
                return provider;
            }
        }
        // Ultimate fallback: the default font
        FontProvider defaultProvider = providers.get(MinecraftFontId.DEFAULT);
        if (defaultProvider != null) {
            return defaultProvider;
        }
        throw new RegistryException("No FontProvider registered and no default font available");
    }

    /**
     * Returns the font for the given ID, bold/italic style flags, and point size.
     *
     * <p>For {@code "minecraft:default"}, the bold/italic flags select the matching style variant
     * ({@link MinecraftFontId#DEFAULT_BOLD}, {@link MinecraftFontId#DEFAULT_ITALIC},
     * {@link MinecraftFontId#DEFAULT_BOLD_ITALIC}). For Galactic and Illageralt, style flags are
     * ignored. Falls back to the regular default font if the requested variant is unavailable.
     *
     * @param fontId the font identifier
     * @param bold   whether to apply bold styling
     * @param italic whether to apply italic styling
     * @param size   the desired point size
     * @return the resolved and sized {@link Font}; never {@code null}
     */
    @Override
    public Font getStyledFont(String fontId, boolean bold, boolean italic, float size) {
        String resolvedId = resolveStyledId(fontId, bold, italic);
        FontProvider provider = providers.get(resolvedId);

        if (provider == null) {
            // Fall back to the plain default if the styled variant isn't registered
            provider = providers.get(MinecraftFontId.DEFAULT);
        }

        if (provider == null) {
            throw new RegistryException("No FontProvider registered for font ID: " + resolvedId
                    + " and no default fallback available");
        }

        Font font = provider.getFont(size);
        if (font == null) {
            throw new RegistryException("FontProvider for " + resolvedId + " returned null font");
        }
        return font;
    }

    /**
     * Returns a fallback font capable of rendering the given Unicode code point.
     *
     * <p>Code points above {@code 0xFFFF} use Unifont Upper; all others use Unifont.
     *
     * @param codePoint the Unicode code point to render
     * @param size      the desired point size
     * @return a {@link Font} that can render the character, or {@code null}
     */
    @Override
    public Font getFallbackFont(int codePoint, float size) {
        String fallbackId = codePoint > 0xFFFF ? MinecraftFontId.UNIFONT_UPPER : MinecraftFontId.UNIFONT;
        FontProvider provider = providers.get(fallbackId);
        return provider != null ? provider.getFont(size) : null;
    }

    /**
     * Measures the total pixel width of the given text using the specified font.
     *
     * @param text   the text to measure; must not be {@code null}
     * @param fontId the font identifier to use for measurement; must not be {@code null}
     * @return the total width in pixels
     * @throws net.aerh.jigsaw.exception.RegistryException if the font is not registered
     */
    @Override
    public int measureWidth(String text, String fontId) {
        Objects.requireNonNull(text, "text must not be null");
        FontProvider provider = resolve(fontId);
        int width = 0;
        for (char c : text.toCharArray()) {
            width += provider.getCharWidth(c);
        }
        return width;
    }

    /**
     * Registers a {@link FontProvider}, replacing any existing provider with the same ID.
     *
     * @param provider the provider to register; must not be {@code null}
     */
    @Override
    public void register(FontProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providers.put(provider.id(), provider);
    }

    /**
     * Maps a font ID and bold/italic flags to the concrete registered font ID.
     */
    private static String resolveStyledId(String fontId, boolean bold, boolean italic) {
        if (MinecraftFontId.DEFAULT.equals(fontId)) {
            if (bold && italic) {
                return MinecraftFontId.DEFAULT_BOLD_ITALIC;
            } else if (bold) {
                return MinecraftFontId.DEFAULT_BOLD;
            } else if (italic) {
                return MinecraftFontId.DEFAULT_ITALIC;
            } else {
                return MinecraftFontId.DEFAULT;
            }
        }
        // Galactic, Illageralt, and any custom fonts are returned as-is (no style variants)
        return fontId;
    }
}
