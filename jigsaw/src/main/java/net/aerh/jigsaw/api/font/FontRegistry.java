package net.aerh.jigsaw.api.font;

/**
 * Central registry for {@link FontProvider} instances.
 * <p>
 * Resolves fonts by ID or by character coverage and computes text metrics.
 *
 * @see FontProvider
 */
public interface FontRegistry {

    /**
     * Returns the {@link FontProvider} registered under the given font ID.
     *
     * @throws net.aerh.jigsaw.exception.RegistryException if no provider is registered for {@code fontId}.
     */
    FontProvider resolve(String fontId);

    /**
     * Returns the best-matching {@link FontProvider} that supports the given character.
     * Falls back to the default font if no specialized provider is found.
     */
    FontProvider resolveForChar(char c);

    /**
     * Measures the pixel width of the given text string when rendered with the named font.
     *
     * @param text   The string to measure.
     * @param fontId The font to use for measurement.
     * @return The total width in pixels.
     */
    int measureWidth(String text, String fontId);

    /**
     * Registers a new {@link FontProvider}.
     * Providers registered later with the same {@link FontProvider#id()} replace earlier ones.
     */
    void register(FontProvider provider);
}
