package net.aerh.jigsaw.core.text;

import java.util.List;

/**
 * The result of laying out a sequence of text segments into wrapped lines.
 *
 * @param lines  The ordered list of lines produced by the layout engine.
 * @param width  The maximum line width in characters across all lines.
 * @param height The number of lines.
 */
public record TextLayout(List<TextLine> lines, int width, int height) {

    public TextLayout {
        lines = List.copyOf(lines);
    }

    /**
     * Returns {@code true} if this layout contains no lines.
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
