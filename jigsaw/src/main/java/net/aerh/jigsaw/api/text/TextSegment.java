package net.aerh.jigsaw.api.text;

/**
 * A run of text that shares a single {@link TextStyle}.
 *
 * @param text  The raw text content of this segment (no formatting codes).
 * @param style The style applied to this segment.
 * @see FormattingParser
 * @see TextStyle
 */
public record TextSegment(String text, TextStyle style) {}
