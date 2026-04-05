package net.aerh.jigsaw.api.font;

import java.awt.Font;

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
     * Returns the font for the given ID, bold/italic style flags, and point size.
     *
     * <p>For {@code "minecraft:default"}, the bold/italic flags select the matching style variant.
     * For {@code "minecraft:alt"} (Galactic) and {@code "minecraft:illageralt"}, bold/italic flags
     * are ignored. Falls back to the default regular font if the requested font is not registered.
     *
     * @param fontId the font identifier
     * @param bold   whether to apply bold styling
     * @param italic whether to apply italic styling
     * @param size   the desired point size
     * @return the resolved and sized {@link Font}; never {@code null}
     */
    Font getStyledFont(String fontId, boolean bold, boolean italic, float size);

    /**
     * Returns a fallback font capable of rendering the given Unicode code point.
     *
     * <p>Code points above {@code 0xFFFF} use Unifont Upper; all others use Unifont.
     * Returns {@code null} if neither fallback font is registered.
     *
     * @param codePoint the Unicode code point to render
     * @param size      the desired point size
     * @return a {@link Font} that can render the character, or {@code null}
     */
    Font getFallbackFont(int codePoint, float size);

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
