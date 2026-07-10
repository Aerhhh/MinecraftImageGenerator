package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.parser.StringParser;
import net.aerh.imagegenerator.text.ChatFormat;
import net.aerh.imagegenerator.text.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Expands {@code %%gradient:#RRGGBB:#RRGGBB[:#RRGGBB...]%%body%%/gradient%%} placeholders into
 * per-character {@code &#rrggbb} color codes, the same per-character form vanilla gradients
 * take in-game. Reference behavior is RGBirdflop's defaults
 * (<a href="https://www.birdflop.com/resources/rgb/">birdflop.com/resources/rgb</a>).
 * <p>
 * Semantics: stops are evenly spaced with linear per-channel RGB interpolation; the position
 * counts every visible character including spaces, but spaces emit no codes. Formatting codes
 * inside the body combine with the gradient and are re-emitted after each per-character color
 * (color codes reset formatting). An explicit color code inside the body overrides the gradient
 * from that point on. After the closer, the pre-gradient color state is restored. Malformed
 * input (invalid hex, fewer than two stops, missing closer) stays literal.
 * <p>
 * Must be registered <em>last</em> in the parser chain so that placeholders inside the body
 * are already resolved to plain text and in-band codes before expansion.
 */
public class GradientParser implements StringParser {

    private static final Pattern OPENER_PATTERN =
        Pattern.compile("%%gradient((?::#[0-9a-fA-F]{6}){2,})%%", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLOSER_PATTERN =
        Pattern.compile("%%/gradient%%", Pattern.CASE_INSENSITIVE);

    private static final String COLOR_CODES = "0123456789abcdef";
    private static final String STYLE_CODES = "klmno";
    private static final String FONT_CODES = "gh";

    @Override
    public String parse(String input) {
        Matcher opener = OPENER_PATTERN.matcher(input);
        Matcher closer = CLOSER_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder(input.length());
        int cursor = 0;

        while (opener.find(cursor)) {
            if (!closer.find(opener.end())) {
                break; // Unclosed opener: everything from the cursor stays literal.
            }

            result.append(input, cursor, opener.start());
            String body = input.substring(opener.end(), closer.start());
            result.append(expand(body, parseStops(opener.group(1)), restorationPrefix(result)));
            cursor = closer.end();
        }

        return result.append(input, cursor, input.length()).toString();
    }

    private static List<RgbColor> parseStops(String stopsGroup) {
        List<RgbColor> stops = new ArrayList<>();

        for (String stop : stopsGroup.split(":")) {
            if (!stop.isEmpty()) {
                stops.add(Objects.requireNonNull(RgbColor.tryParse(stop), stop));
            }
        }

        return stops;
    }

    /**
     * Expands a gradient body into per-character color codes. {@code restoration} is appended
     * only when the gradient actually colored something and ran to the end of the body; an
     * explicit inner color code takes over instead and needs no restoration.
     */
    private static String expand(String body, List<RgbColor> stops, String restoration) {
        StringBuilder out = new StringBuilder(body.length() * (RgbColor.HEX_CODE_LENGTH + 2));
        StringBuilder activeStyles = new StringBuilder();
        int visibleCount = visibleLength(body);
        int visibleIndex = 0;
        boolean colored = false;

        for (int i = 0; i < body.length(); i++) {
            char symbol = body.charAt(i);
            CodeType type = classifyCode(body, i);

            if (type == CodeType.HEX_COLOR || type == CodeType.NAMED_COLOR) {
                // An explicit color code overrides the gradient from this point on.
                return out.append(body, i, body.length()).toString();
            }
            if (type == CodeType.STYLE) {
                String style = "" + ChatFormat.AMPERSAND_SYMBOL + Character.toLowerCase(body.charAt(i + 1));
                if (activeStyles.indexOf(style) == -1) {
                    activeStyles.append(style);
                }
                i += codeSkipLength(type);
                continue;
            }
            if (type == CodeType.RESET) {
                activeStyles.setLength(0);
                out.append(symbol).append(body.charAt(i + 1));
                i += codeSkipLength(type);
                continue;
            }
            if (type == CodeType.FONT) {
                // Fonts survive color changes in the lexer, so passing them through once suffices.
                out.append(symbol).append(body.charAt(i + 1));
                i += codeSkipLength(type);
                continue;
            }

            if (Character.isWhitespace(symbol)) {
                // Spaces advance the gradient but a color code on a space would be invisible.
                out.append(symbol);
                visibleIndex++;
                continue;
            }

            RgbColor color = sampleGradient(stops, position(visibleIndex, visibleCount));
            out.append(ChatFormat.AMPERSAND_SYMBOL).append(color.toJsonString())
                .append(activeStyles).append(symbol);
            visibleIndex++;
            colored = true;
        }

        return colored ? out.append(restoration).toString() : out.toString();
    }

    /**
     * Derives the color state active just before the gradient opener so it can be re-applied
     * after the closer: a reset followed by the last color, font, and style codes seen since
     * the last reset. Intentionally not shared with TextWrapper.FormatState, whose accumulating
     * treatment of reset codes is a wrapper-specific quirk.
     */
    private static String restorationPrefix(CharSequence before) {
        String lastColor = "";
        String lastFont = "";
        StringBuilder styles = new StringBuilder();

        for (int i = 0; i < before.length() - 1; i++) {
            CodeType type = classifyCode(before, i);

            switch (type) {
                case HEX_COLOR:
                    lastColor = ChatFormat.AMPERSAND_SYMBOL
                        + before.subSequence(i + 1, i + 1 + RgbColor.HEX_CODE_LENGTH).toString().toLowerCase(Locale.ROOT);
                    styles.setLength(0);
                    break;
                case NAMED_COLOR:
                    lastColor = "" + ChatFormat.AMPERSAND_SYMBOL + Character.toLowerCase(before.charAt(i + 1));
                    styles.setLength(0);
                    break;
                case STYLE: {
                    String style = "" + ChatFormat.AMPERSAND_SYMBOL + Character.toLowerCase(before.charAt(i + 1));
                    if (styles.indexOf(style) == -1) {
                        styles.append(style);
                    }
                    break;
                }
                case FONT:
                    lastFont = "" + ChatFormat.AMPERSAND_SYMBOL + Character.toLowerCase(before.charAt(i + 1));
                    break;
                case RESET:
                    lastColor = "";
                    lastFont = "";
                    styles.setLength(0);
                    break;
                case NONE:
                    break;
            }

            i += codeSkipLength(type);
        }

        return "" + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.RESET.getCode() + lastColor + lastFont + styles;
    }

    /** Counts characters that render (in-band codes are zero-width), including whitespace. */
    private static int visibleLength(String body) {
        int count = 0;

        for (int i = 0; i < body.length(); i++) {
            CodeType type = classifyCode(body, i);

            if (type != CodeType.NONE) {
                i += codeSkipLength(type);
                continue;
            }

            count++;
        }

        return count;
    }

    /** Position of a character within the gradient: {@code t = i / (len - 1)}, first stop when len is 1. */
    private static double position(int visibleIndex, int visibleCount) {
        return visibleCount <= 1 ? 0.0 : (double) visibleIndex / (visibleCount - 1);
    }

    /** Samples the multi-stop gradient at {@code t} in [0, 1]; stops are evenly spaced. */
    private static RgbColor sampleGradient(List<RgbColor> stops, double t) {
        double scaled = t * (stops.size() - 1);
        int index = Math.min((int) scaled, stops.size() - 2);
        return RgbColor.lerp(stops.get(index), stops.get(index + 1), scaled - index);
    }

    private static boolean isCodeSymbol(char c) {
        return c == ChatFormat.AMPERSAND_SYMBOL || c == ChatFormat.SECTION_SYMBOL;
    }

    /** The kind of in-band code (if any) a code symbol introduces. */
    private enum CodeType {
        HEX_COLOR, NAMED_COLOR, STYLE, FONT, RESET, NONE
    }

    /**
     * Classifies the in-band code starting at index {@code i}, or {@code NONE} when the char at
     * {@code i} is not a code symbol, or is a code symbol not followed by a recognized code.
     * This is the single place the code grammar (hex colors, legacy colors, styles, fonts,
     * reset) is defined; {@link #expand}, {@link #restorationPrefix}, and {@link #visibleLength}
     * all classify through here so their skip lengths and recognized codes stay in sync.
     */
    private static CodeType classifyCode(CharSequence text, int i) {
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

    /** Number of extra characters (after the code symbol itself) the code at {@code type} occupies. */
    private static int codeSkipLength(CodeType type) {
        if (type == CodeType.NONE) {
            return 0;
        }

        return type == CodeType.HEX_COLOR ? RgbColor.HEX_CODE_LENGTH : 1;
    }
}
