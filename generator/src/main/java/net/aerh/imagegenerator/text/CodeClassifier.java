package net.aerh.imagegenerator.text;

import org.jetbrains.annotations.NotNull;

import java.util.regex.Pattern;

/**
 * Single source of truth for the legacy in-band code grammar: which characters introduce a
 * code ({@code &}/{@code §}), which codes exist (hex colors, named colors, styles, fonts,
 * reset), how many characters each occupies, and the two-character {@code \n} line-break
 * marker. {@code GradientParser} and {@code TextWrapper} both lex through this class so the
 * grammar cannot drift between them.
 * <p>
 * The code alphabet is derived from {@link ChatFormat}, so a new enum constant is picked up
 * here automatically.
 * <p>
 * Consumers deliberately differ in what they do with a classification:
 * <ul>
 *   <li>{@code TextWrapper} counts {@link CodeType#FONT} codes as visible characters when
 *       measuring line width, while {@code GradientParser} treats them as zero-width when
 *       counting gradient positions. This inconsistency predates the shared classifier and
 *       the wrapper side is pinned by existing wrapped-line expectations, so it is preserved
 *       and documented (see {@code TextWrapper#isZeroWidthWhenWrapping}) rather than
 *       resolved.</li>
 *   <li>{@code TextWrapper.FormatState} accumulates {@link CodeType#RESET} like a style code
 *       instead of clearing carried-over state; see the note there.</li>
 * </ul>
 */
public final class CodeClassifier {

    /** The two-character line-break marker (literal backslash followed by 'n') used in lore text. */
    public static final String NEWLINE_MARKER = "\\n";

    /** Regex matching a single line break: a real newline or the {@link #NEWLINE_MARKER}. */
    public static final String NEWLINE_REGEX = "(?:\n|" + Pattern.quote(NEWLINE_MARKER) + ")";

    private static final String COLOR_CODES;
    private static final String STYLE_CODES;
    private static final String FONT_CODES;

    static {
        StringBuilder colors = new StringBuilder();
        StringBuilder styles = new StringBuilder();
        StringBuilder fonts = new StringBuilder();

        for (ChatFormat format : ChatFormat.VALUES) {
            if (format.isColor()) {
                colors.append(format.getCode());
            } else if (format.isFont()) {
                fonts.append(format.getCode());
            } else if (format.isFormat()) {
                styles.append(format.getCode());
            }
        }

        COLOR_CODES = colors.toString();
        STYLE_CODES = styles.toString();
        FONT_CODES = fonts.toString();
    }

    private CodeClassifier() {
    }

    /** The kind of in-band code (if any) a code symbol introduces. */
    public enum CodeType {
        HEX_COLOR, NAMED_COLOR, STYLE, FONT, RESET, NONE
    }

    /** Whether {@code c} is one of the two characters that can introduce an in-band code. */
    public static boolean isCodeSymbol(char c) {
        return c == ChatFormat.AMPERSAND_SYMBOL || c == ChatFormat.SECTION_SYMBOL;
    }

    /**
     * Classifies the in-band code starting at index {@code i}, or {@link CodeType#NONE} when
     * the char at {@code i} is not a code symbol, or is a code symbol not followed by a
     * recognized code. Classification is case-insensitive; the caller decides whether to
     * preserve the original casing of the matched characters.
     *
     * @param text the text to classify within
     * @param i    the index of the potential code symbol
     *
     * @return the classified code type
     */
    public static @NotNull CodeType classify(@NotNull CharSequence text, int i) {
        if (!isCodeSymbol(text.charAt(i)) || i + 1 >= text.length()) {
            return CodeType.NONE;
        }

        char peek = Character.toLowerCase(text.charAt(i + 1));

        if (peek == '#' && RgbColor.tryParseAt(text, i + 1) != null) {
            return CodeType.HEX_COLOR;
        }
        if (COLOR_CODES.indexOf(peek) != -1) {
            return CodeType.NAMED_COLOR;
        }
        if (STYLE_CODES.indexOf(peek) != -1) {
            return CodeType.STYLE;
        }
        if (FONT_CODES.indexOf(peek) != -1) {
            return CodeType.FONT;
        }
        if (peek == ChatFormat.RESET.getCode()) {
            return CodeType.RESET;
        }

        return CodeType.NONE;
    }

    /**
     * Number of extra characters (after the code symbol itself) a code of {@code type}
     * occupies: {@link RgbColor#HEX_CODE_LENGTH} for hex colors, one for every other code,
     * zero for {@link CodeType#NONE}.
     *
     * @param type the classified code type
     *
     * @return the number of characters to skip past the code symbol
     */
    public static int skipLength(@NotNull CodeType type) {
        if (type == CodeType.NONE) {
            return 0;
        }

        return type == CodeType.HEX_COLOR ? RgbColor.HEX_CODE_LENGTH : 1;
    }

    /**
     * Recognizes the two-character {@link #NEWLINE_MARKER} starting at index {@code i}. It is
     * not an in-band code, so it is not part of {@link #classify(CharSequence, int)}.
     *
     * @param text the text to check within
     * @param i    the index of the potential marker start
     *
     * @return true if the marker starts at {@code i}
     */
    public static boolean isNewlineMarker(@NotNull CharSequence text, int i) {
        return text.charAt(i) == NEWLINE_MARKER.charAt(0)
            && i + 1 < text.length()
            && text.charAt(i + 1) == NEWLINE_MARKER.charAt(1);
    }
}
