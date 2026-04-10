package net.aerh.jigsaw.core.text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for pre-processing raw user input text before it enters
 * the tooltip generation pipeline.
 */
public final class TextWrapper {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile(
            "[&§]#[0-9a-fA-F]{6}|[&§][xX](?:[&§][0-9a-fA-F]){6}|[&§][0-9a-fA-FK-ORk-or]");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("(?:\n|\\\\n)");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\S+|\\s+");

    private TextWrapper() {
    }

    // -----------------------------------------------------------------------
    // Inner type
    // -----------------------------------------------------------------------

    /**
     * Holds the last color code and active formatting codes to carry over between lines/segments.
     */
    private record FormatState(String lastColor, String formattingCodes) {

        /** Represents the initial state with no formatting. */
        private static final FormatState EMPTY = new FormatState("", "");

        /**
         * Creates the formatting prefix string (e.g., {@code &c&l}) to prepend to a new line/segment.
         */
        public String prefix() {
            return lastColor + formattingCodes;
        }

        /**
         * Calculates the formatting state at the end of a given segment based on the state at the
         * beginning of it.
         *
         * @param segment      the text segment to analyse
         * @param initialState the {@link FormatState} before this segment
         * @return the {@link FormatState} after processing this segment
         */
        public static FormatState deriveStateFromSegment(String segment, FormatState initialState) {
            String lastColor = initialState.lastColor();
            StringBuilder formatting = new StringBuilder(initialState.formattingCodes());

            for (int i = 0; i < segment.length(); i++) {
                if ((segment.charAt(i) == '&' || segment.charAt(i) == '§') && i + 1 < segment.length()) {
                    char code = Character.toLowerCase(segment.charAt(i + 1));
                    String codeStr = segment.substring(i, i + 2);

                    if ("0123456789abcdef".indexOf(code) != -1) {
                        lastColor = codeStr;
                        formatting = new StringBuilder(); // reset formatting when color changes
                        i++;
                    } else if ("klmnor".indexOf(code) != -1) {
                        if (formatting.indexOf(codeStr) == -1) {
                            formatting.append(codeStr);
                        }
                        i++;
                    }
                }
            }

            return new FormatState(lastColor, formatting.toString());
        }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Wraps a string to a specified maximum line length, preserving Minecraft formatting codes.
     *
     * <p>Wrapping occurs at word boundaries. If a single word exceeds {@code maxLineLength} it is
     * split mid-word. Formatting codes ({@code &} / {@code §} sequences) are carried over to each
     * wrapped continuation line so colors are not lost.
     *
     * @param input         the input string, potentially containing formatting codes and literal
     *                      {@code \n} newline markers
     * @param maxLineLength the maximum <em>visible</em> character count per line; must be &gt; 0.
     *                      When &lt;= 0 the input is returned as a single-element list unchanged.
     * @return an ordered list of wrapped lines; never {@code null}
     */
    public static List<String> wrapString(String input, int maxLineLength) {
        List<String> lines = new ArrayList<>();

        if (input == null) {
            return lines;
        }

        if (input.isEmpty()) {
            lines.add("");
            return lines;
        }

        if (maxLineLength <= 0) {
            lines.add(input);
            return lines;
        }

        String normalizedInput = normalizeNewlines(input);
        String[] rawLines = NEWLINE_PATTERN.split(normalizedInput, -1);
        FormatState currentFormatState = FormatState.EMPTY;

        for (String rawLine : rawLines) {
            if (rawLine.isEmpty()) {
                lines.add(""); // preserve empty lines between paragraphs
                currentFormatState = FormatState.EMPTY;
                continue;
            }

            StringBuilder currentLineBuilder = new StringBuilder();
            int currentVisibleLength = 0;

            Matcher tokenMatcher = TOKEN_PATTERN.matcher(rawLine);
            while (tokenMatcher.find()) {
                String token = tokenMatcher.group();

                if (token.trim().isEmpty()) {
                    int whitespaceLength = token.length();

                    // If whitespace alone would exceed the line, wrap before adding more spaces
                    if (currentVisibleLength + whitespaceLength > maxLineLength && !currentLineBuilder.isEmpty()) {
                        String finishedLine = currentLineBuilder.toString();
                        lines.add(currentFormatState.prefix() + finishedLine);
                        currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
                        currentLineBuilder = new StringBuilder();
                        currentVisibleLength = 0;
                    }

                    boolean manualIndent = tokenMatcher.start() == 0;
                    if (currentLineBuilder.isEmpty() && !manualIndent) {
                        // skip leading whitespace that originates from automatic wrapping
                        continue;
                    }

                    currentLineBuilder.append(token);
                    currentVisibleLength += whitespaceLength;
                    continue;
                }

                String strippedWord = stripColorCodes(token);
                int wordVisibleLength = strippedWord.length();

                if (wordVisibleLength > maxLineLength) {
                    // Flush the current line before splitting the long word
                    if (!currentLineBuilder.isEmpty()) {
                        String finishedLine = currentLineBuilder.toString();
                        lines.add(currentFormatState.prefix() + finishedLine);
                        currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
                        currentLineBuilder = new StringBuilder();
                        currentVisibleLength = 0;
                    }

                    currentFormatState = splitLongWord(token, maxLineLength, lines, currentFormatState);
                    currentVisibleLength = 0;
                } else if (currentVisibleLength + wordVisibleLength <= maxLineLength) {
                    currentLineBuilder.append(token);
                    currentVisibleLength += wordVisibleLength;
                } else {
                    String finishedLine = currentLineBuilder.toString();
                    lines.add(currentFormatState.prefix() + finishedLine);
                    currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
                    currentLineBuilder = new StringBuilder(token);
                    currentVisibleLength = wordVisibleLength;
                }
            }

            // Flush any remaining text for this paragraph line
            if (!currentLineBuilder.isEmpty()) {
                String finishedLine = currentLineBuilder.toString();
                lines.add(currentFormatState.prefix() + finishedLine);
                currentFormatState = FormatState.deriveStateFromSegment(finishedLine, currentFormatState);
            }
        }

        return lines;
    }

    /**
     * Normalizes newline handling so that combinations of actual newlines and literal
     * {@code \n} markers are treated as a single line break.
     *
     * @param input the input string to normalize
     * @return the normalized string, or the original value if {@code null} or empty
     */
    public static String normalizeNewlines(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        if (input.indexOf('\r') != -1) {
            input = input.replace("\r\n", "\n").replace('\r', '\n');
        }

        StringBuilder normalized = new StringBuilder(input.length());

        for (int i = 0; i < input.length(); ) {
            char current = input.charAt(i);

            if (current == '\n') {
                normalized.append('\n');
                i++;

                // consume any immediately-following literal \n markers
                while (i + 1 < input.length() && input.charAt(i) == '\\' && input.charAt(i + 1) == 'n') {
                    i += 2;
                }
                continue;
            }

            if (current == '\\' && i + 1 < input.length() && input.charAt(i + 1) == 'n') {
                // if the literal \n is immediately followed by an actual newline, skip the marker
                if (i + 2 < input.length() && input.charAt(i + 2) == '\n') {
                    i += 2;
                    continue;
                }

                normalized.append('\n');
                i += 2;
                continue;
            }

            normalized.append(current);
            i++;
        }

        return normalized.toString();
    }

    /**
     * Strips all known Minecraft color and formatting codes from a string.
     *
     * @param string the string to strip codes from
     * @return a plain string with codes removed, or an empty string if input is {@code null} or empty
     */
    public static String stripColorCodes(String string) {
        if (string == null || string.isEmpty()) {
            return "";
        }

        return STRIP_COLOR_PATTERN.matcher(string).replaceAll("");
    }

    /**
     * Replaces actual newline characters (from pasting multi-line text or pressing
     * Shift+Enter in Discord) with spaces, preserving literal {@code \n} markers
     * (backslash + n) as intentional line breaks.
     * <p>
     * Call this on raw user input <b>before</b> passing it to the generator so that
     * editor line breaks do not create unwanted lore lines.
     *
     * @param input the raw user input string
     * @return the input with actual newlines replaced by spaces, or the original value
     *         if {@code null} or empty
     */
    public static String stripActualNewlines(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        // Replace actual newlines (from Discord multiline input) and any surrounding
        // whitespace with nothing. Users use literal \n for intended line breaks, so
        // real newlines are just formatting artifacts from their text editor.
        return input.replaceAll("\\s*\\r?\\n\\s*", "");
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Splits a single word that is longer than {@code maxLineLength} into multiple lines,
     * preserving formatting codes across each segment.
     *
     * @param word          the word to split
     * @param maxLineLength the maximum visible length per line
     * @param lines         the list to add the split segments to
     * @param initialState  the {@link FormatState} entering this word
     * @return the {@link FormatState} at the end of the split word
     */
    private static FormatState splitLongWord(String word, int maxLineLength, List<String> lines, FormatState initialState) {
        FormatState currentWordFormatState = initialState;
        int currentActualIndex = 0;

        while (currentActualIndex < word.length()) {
            int currentVisibleLength = 0;
            int segmentEndActualIndex = currentActualIndex;

            for (int i = currentActualIndex; i < word.length(); ) {
                char currentChar = word.charAt(i);

                if ((currentChar == '&' || currentChar == '§') && i + 1 < word.length()) {
                    char code = Character.toLowerCase(word.charAt(i + 1));

                    if ("0123456789abcdefklmnor".indexOf(code) != -1) {
                        i += 2; // formatting codes do not count towards visible length
                    } else {
                        currentVisibleLength++;
                        i++;
                    }
                } else {
                    currentVisibleLength++;
                    i++;
                }

                segmentEndActualIndex = i;

                if (currentVisibleLength >= maxLineLength) {
                    break;
                }
            }

            String lineSegment = word.substring(currentActualIndex, segmentEndActualIndex);
            lines.add(currentWordFormatState.prefix() + lineSegment);

            currentWordFormatState = FormatState.deriveStateFromSegment(lineSegment, currentWordFormatState);
            currentActualIndex = segmentEndActualIndex;
        }

        return currentWordFormatState;
    }
}
