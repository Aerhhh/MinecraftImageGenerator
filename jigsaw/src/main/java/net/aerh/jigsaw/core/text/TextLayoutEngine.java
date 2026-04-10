package net.aerh.jigsaw.core.text;

import net.aerh.jigsaw.api.text.TextSegment;
import net.aerh.jigsaw.api.text.TextStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out a list of {@link TextSegment}s into a {@link TextLayout} by wrapping text
 * at a character-count boundary defined in {@link TextLayoutOptions}.
 * <p>
 * Newlines ({@code \n}) in segment text are treated as explicit line breaks.
 * Words are kept together where possible; a word that exceeds {@code maxWidth} on its own
 * is hard-wrapped at the boundary.
 */
public final class TextLayoutEngine {

    private TextLayoutEngine() {}

    /**
     * Lays out the given segments into wrapped lines.
     *
     * @param segments The segments to lay out.
     * @param options  Layout configuration.
     * @return A {@link TextLayout} with computed dimensions. Returns an empty layout for empty input.
     */
    public static TextLayout layout(List<TextSegment> segments, TextLayoutOptions options) {
        if (segments == null || segments.isEmpty()) {
            return new TextLayout(List.of(), 0, 0);
        }

        int maxWidth = options.maxWidth();

        // We build a sequence of "tokens" - either a segment's full text or a portion of it
        // bounded by explicit newlines. We then word-wrap.
        List<TextLine> lines = new ArrayList<>();
        List<TextSegment> currentLine = new ArrayList<>();
        int currentWidth = 0;
        int maxLineWidth = 0;

        for (TextSegment segment : segments) {
            String text = segment.text();
            TextStyle style = segment.style();

            // Split on newlines to handle explicit line breaks
            String[] parts = text.split("\n", -1);
            for (int partIndex = 0; partIndex < parts.length; partIndex++) {
                String part = parts[partIndex];

                // If this is not the first part in the split, flush the current line first
                if (partIndex > 0) {
                    maxLineWidth = Math.max(maxLineWidth, currentWidth);
                    lines.add(new TextLine(List.copyOf(currentLine), currentWidth));
                    currentLine = new ArrayList<>();
                    currentWidth = 0;
                }

                // Word-wrap within this part
                String[] words = part.split("(?<= )|(?= )", -1);
                StringBuilder pending = new StringBuilder();

                for (String word : words) {
                    if (word.isEmpty()) {
                        continue;
                    }
                    if (currentWidth + pending.length() + word.length() > maxWidth && currentWidth + pending.length() > 0) {
                        // Flush pending into current line, then wrap
                        if (pending.length() > 0) {
                            currentLine.add(new TextSegment(pending.toString(), style));
                            currentWidth += pending.length();
                            pending.setLength(0);
                        }
                        maxLineWidth = Math.max(maxLineWidth, currentWidth);
                        lines.add(new TextLine(List.copyOf(currentLine), currentWidth));
                        currentLine = new ArrayList<>();
                        currentWidth = 0;
                    }

                    // If the word itself exceeds maxWidth, hard-wrap it
                    while (word.length() > maxWidth) {
                        int space = maxWidth - currentWidth - pending.length();
                        if (space <= 0) {
                            if (pending.length() > 0) {
                                currentLine.add(new TextSegment(pending.toString(), style));
                                currentWidth += pending.length();
                                pending.setLength(0);
                            }
                            maxLineWidth = Math.max(maxLineWidth, currentWidth);
                            lines.add(new TextLine(List.copyOf(currentLine), currentWidth));
                            currentLine = new ArrayList<>();
                            currentWidth = 0;
                            space = maxWidth;
                        }
                        String chunk = word.substring(0, space);
                        word = word.substring(space);
                        pending.append(chunk);
                        currentLine.add(new TextSegment(pending.toString(), style));
                        currentWidth += pending.length();
                        pending.setLength(0);
                        maxLineWidth = Math.max(maxLineWidth, currentWidth);
                        lines.add(new TextLine(List.copyOf(currentLine), currentWidth));
                        currentLine = new ArrayList<>();
                        currentWidth = 0;
                    }

                    pending.append(word);
                }

                // Flush any remaining pending text into the current line buffer
                if (pending.length() > 0) {
                    currentLine.add(new TextSegment(pending.toString(), style));
                    currentWidth += pending.length();
                }
            }
        }

        // Flush final line
        if (!currentLine.isEmpty() || !lines.isEmpty()) {
            maxLineWidth = Math.max(maxLineWidth, currentWidth);
            if (!currentLine.isEmpty()) {
                lines.add(new TextLine(List.copyOf(currentLine), currentWidth));
            }
        }

        if (lines.isEmpty()) {
            return new TextLayout(List.of(), 0, 0);
        }

        return new TextLayout(lines, maxLineWidth, lines.size());
    }
}
