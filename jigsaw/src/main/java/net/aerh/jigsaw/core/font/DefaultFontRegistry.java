package net.aerh.jigsaw.core.font;

import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.exception.RegistryException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A {@link FontRegistry} that is pre-populated with the built-in Minecraft font providers.
 *
 * <p>Use {@link #withBuiltins()} to obtain an instance with all standard fonts registered.
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

    private static final String FONT_PATH_DEFAULT  = "minecraft/assets/fonts/unifont-17.0.03.otf";
    private static final String FONT_PATH_GALACTIC  = "minecraft/assets/fonts/unifont_upper-17.0.03.otf";
    private static final String FONT_PATH_ILLAGERALT = "minecraft/assets/fonts/unifont_upper-17.0.03.otf";

    private final ConcurrentHashMap<String, FontProvider> providers = new ConcurrentHashMap<>();

    private DefaultFontRegistry() {}

    /**
     * Creates a {@link DefaultFontRegistry} with the three standard Minecraft font providers
     * pre-registered.
     */
    public static DefaultFontRegistry withBuiltins() {
        DefaultFontRegistry registry = new DefaultFontRegistry();
        registry.register(new ResourceFontProvider(MinecraftFontId.DEFAULT,   FONT_PATH_DEFAULT));
        registry.register(new ResourceFontProvider(MinecraftFontId.GALACTIC,  FONT_PATH_GALACTIC));
        registry.register(new ResourceFontProvider(MinecraftFontId.ILLAGERALT, FONT_PATH_ILLAGERALT));
        return registry;
    }

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

    @Override
    public void register(FontProvider provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        providers.put(provider.id(), provider);
    }
}
