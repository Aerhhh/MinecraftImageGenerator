package net.aerh.jigsaw.core.text;

/**
 * Utility methods for pre-processing raw user input text before it enters
 * the tooltip generation pipeline.
 */
public final class TextWrapper {

    private TextWrapper() {
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

        return input.replace("\r\n", " ").replace("\r", " ").replace("\n", " ");
    }
}
