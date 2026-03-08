package net.aerh.imagegenerator.text;

import net.aerh.imagegenerator.text.segment.ColorSegment;
import net.aerh.imagegenerator.text.segment.LineSegment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight parser for angle-bracket text formatting tags.
 * <p>
 * Supported tags:
 * <ul>
 *   <li>Named colors: {@code <red>}, {@code <gold>}, {@code <dark_blue>}, etc.</li>
 *   <li>Hex colors: {@code <#FF5555>} or {@code <color:#FF5555>}</li>
 *   <li>Decorations: {@code <bold>}/{@code <b>}, {@code <italic>}/{@code <i>}/{@code <em>},
 *       {@code <underlined>}/{@code <u>}, {@code <strikethrough>}/{@code <st>},
 *       {@code <obfuscated>}/{@code <obf>}</li>
 *   <li>Gradient: {@code <gradient:color1:color2:...>text</gradient>}</li>
 *   <li>Rainbow: {@code <rainbow>text</rainbow>} or {@code <rainbow:phase>text</rainbow>}</li>
 *   <li>Reset: {@code <reset>} or {@code <r>}</li>
 * </ul>
 * <p>
 * Closing tags use the {@code </tagname>} format. Unrecognized tags are treated as literal text.
 */
public final class TagTextParser {

    private static final Map<String, ChatFormat> NAMED_COLORS = buildNamedColorMap();

    private static final Map<String, Decoration> DECORATION_ALIASES = Map.ofEntries(
        Map.entry("bold", Decoration.BOLD),
        Map.entry("b", Decoration.BOLD),
        Map.entry("italic", Decoration.ITALIC),
        Map.entry("i", Decoration.ITALIC),
        Map.entry("em", Decoration.ITALIC),
        Map.entry("underlined", Decoration.UNDERLINED),
        Map.entry("u", Decoration.UNDERLINED),
        Map.entry("strikethrough", Decoration.STRIKETHROUGH),
        Map.entry("st", Decoration.STRIKETHROUGH),
        Map.entry("obfuscated", Decoration.OBFUSCATED),
        Map.entry("obf", Decoration.OBFUSCATED)
    );

    private TagTextParser() {
    }

    /**
     * Parses a single line of tag-formatted text into a {@link LineSegment}.
     * Does not handle newline splitting - callers should split lines before calling this.
     */
    public static @NotNull LineSegment parseLine(@NotNull String input) {
        List<ColorSegment> segments = new ArrayList<>();

        FormatState state = new FormatState();
        Deque<DeferredTag> tagStack = new ArrayDeque<>();
        StringBuilder textBuffer = new StringBuilder();

        int i = 0;
        while (i < input.length()) {
            char c = input.charAt(i);

            // Try to parse a tag
            if (c == '<') {
                int tagEnd = input.indexOf('>', i);
                if (tagEnd == -1) {
                    textBuffer.append(c);
                    i++;
                    continue;
                }

                String tagContent = input.substring(i + 1, tagEnd);
                boolean closing = tagContent.startsWith("/");
                if (closing) {
                    tagContent = tagContent.substring(1);
                }

                String tagName = tagContent.split(":")[0].toLowerCase(Locale.ROOT);
                String[] args = tagContent.split(":");

                if (closing) {
                    // Handle closing tag
                    if (tagName.equals("gradient") || tagName.equals("rainbow")) {
                        flushText(textBuffer, state, segments);
                        // Find the matching opening tag
                        DeferredTag deferred = popMatching(tagStack, tagName);
                        if (deferred != null) {
                            applyDeferredTag(deferred, segments);
                            state = deferred.stateBefore.copy();
                        }
                    } else if (DECORATION_ALIASES.containsKey(tagName)) {
                        flushText(textBuffer, state, segments);
                        Decoration dec = DECORATION_ALIASES.get(tagName);
                        setDecoration(state, dec, false);
                    } else if (NAMED_COLORS.containsKey(tagName) || tagName.startsWith("#") || tagName.equals("color")) {
                        flushText(textBuffer, state, segments);
                        state.color = null; // reset to default
                    } else if (tagName.equals("reset") || tagName.equals("r")) {
                        flushText(textBuffer, state, segments);
                        state = new FormatState();
                    }
                    i = tagEnd + 1;
                    continue;
                }

                // Handle opening tag
                if (tagName.equals("gradient") || tagName.equals("rainbow")) {
                    flushText(textBuffer, state, segments);
                    DeferredTag deferred = new DeferredTag(tagName, args, state.copy());
                    deferred.startSegmentIndex = segments.size();
                    tagStack.push(deferred);
                    i = tagEnd + 1;
                    continue;
                }

                if (tagName.equals("reset") || tagName.equals("r")) {
                    flushText(textBuffer, state, segments);
                    state = new FormatState();
                    i = tagEnd + 1;
                    continue;
                }

                // Decorations
                Decoration dec = DECORATION_ALIASES.get(tagName);
                if (dec != null) {
                    flushText(textBuffer, state, segments);
                    setDecoration(state, dec, true);
                    i = tagEnd + 1;
                    continue;
                }

                // Color tags
                Color resolvedColor = resolveTagColor(tagName, args);
                if (resolvedColor != null) {
                    flushText(textBuffer, state, segments);
                    state.color = resolvedColor;
                    i = tagEnd + 1;
                    continue;
                }

                // Unrecognized tag - treat as literal text
                textBuffer.append(input, i, tagEnd + 1);
                i = tagEnd + 1;
                continue;
            }

            textBuffer.append(c);
            i++;
        }

        flushText(textBuffer, state, segments);

        if (segments.isEmpty()) {
            segments.add(new ColorSegment(""));
        }
        return LineSegment.builder().withSegments(segments).build();
    }

    /**
     * Returns true if the input appears to contain angle-bracket formatting tags.
     */
    public static boolean containsTags(@NotNull String input) {
        return input.contains("<") && input.contains(">");
    }

    private static void flushText(StringBuilder buffer, FormatState state, List<ColorSegment> segments) {
        if (buffer.isEmpty()) {
            return;
        }

        ColorSegment segment = new ColorSegment(buffer.toString());
        if (state.color != null) {
            segment.setColor(state.color);
        }
        segment.setBold(state.bold);
        segment.setItalic(state.italic);
        segment.setUnderlined(state.underlined);
        segment.setStrikethrough(state.strikethrough);
        segment.setObfuscated(state.obfuscated);
        segments.add(segment);
        buffer.setLength(0);
    }

    private static @Nullable Color resolveTagColor(String tagName, String[] args) {
        // <#FF5555>
        if (tagName.startsWith("#")) {
            return parseHex(tagName);
        }

        // <color:#FF5555> or <color:red>
        if (tagName.equals("color") && args.length >= 2) {
            String colorArg = args[1];
            if (colorArg.startsWith("#")) {
                return parseHex(colorArg);
            }
            ChatFormat named = NAMED_COLORS.get(colorArg.toLowerCase(Locale.ROOT));
            if (named != null) {
                return named.getColor();
            }
            return null;
        }

        // <red>, <gold>, etc.
        ChatFormat named = NAMED_COLORS.get(tagName);
        if (named != null) {
            return named.getColor();
        }

        return null;
    }

    private static @Nullable Color parseHex(String hex) {
        if (hex == null || !hex.startsWith("#") || hex.length() != 7) {
            return null;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void setDecoration(FormatState state, Decoration dec, boolean value) {
        switch (dec) {
            case BOLD -> state.bold = value;
            case ITALIC -> state.italic = value;
            case UNDERLINED -> state.underlined = value;
            case STRIKETHROUGH -> state.strikethrough = value;
            case OBFUSCATED -> state.obfuscated = value;
        }
    }

    private static @Nullable DeferredTag popMatching(Deque<DeferredTag> stack, String tagName) {
        for (var it = stack.iterator(); it.hasNext(); ) {
            DeferredTag tag = it.next();
            if (tag.tagName.equals(tagName)) {
                it.remove();
                return tag;
            }
        }
        return null;
    }

    private static void applyDeferredTag(DeferredTag tag, List<ColorSegment> segments) {
        // Collect all text from segments added since the tag opened
        StringBuilder fullText = new StringBuilder();
        for (int i = tag.startSegmentIndex; i < segments.size(); i++) {
            fullText.append(segments.get(i).getText());
        }

        String text = fullText.toString();
        if (text.isEmpty()) {
            return;
        }

        // Generate per-character colors
        Color[] colors;
        if (tag.tagName.equals("gradient")) {
            colors = computeGradientColors(text.length(), tag.args);
        } else {
            // rainbow
            float phase = 0f;
            if (tag.args.length >= 2) {
                try {
                    phase = Float.parseFloat(tag.args[1].replace("!", ""));
                } catch (NumberFormatException ignored) {
                }
            }
            colors = computeRainbowColors(text.length(), phase);
        }

        // Remove the original segments and replace with per-character colored ones
        while (segments.size() > tag.startSegmentIndex) {
            segments.remove(segments.size() - 1);
        }

        FormatState baseState = tag.stateBefore;
        for (int ci = 0; ci < text.length(); ci++) {
            ColorSegment charSeg = new ColorSegment(String.valueOf(text.charAt(ci)));
            charSeg.setColor(colors[ci]);
            charSeg.setBold(baseState.bold);
            charSeg.setItalic(baseState.italic);
            charSeg.setUnderlined(baseState.underlined);
            charSeg.setStrikethrough(baseState.strikethrough);
            charSeg.setObfuscated(baseState.obfuscated);
            segments.add(charSeg);
        }
    }

    static Color[] computeGradientColors(int length, String[] args) {
        // Parse gradient colors from args: <gradient:color1:color2:...>
        List<Color> stops = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            String arg = args[i].trim();
            Color c = parseHex(arg.startsWith("#") ? arg : null);
            if (c == null) {
                ChatFormat named = NAMED_COLORS.get(arg.toLowerCase(Locale.ROOT));
                if (named != null) {
                    c = named.getColor();
                }
            }
            if (c != null) {
                stops.add(c);
            }
        }

        if (stops.size() < 2) {
            // Not enough colors for a gradient, fill with gray
            Color[] fallback = new Color[length];
            java.util.Arrays.fill(fallback, ChatFormat.GRAY.getColor());
            return fallback;
        }

        Color[] colors = new Color[length];
        if (length == 1) {
            colors[0] = stops.get(0);
            return colors;
        }

        for (int i = 0; i < length; i++) {
            float t = (float) i / (length - 1);
            float scaledT = t * (stops.size() - 1);
            int stopIndex = Math.min((int) scaledT, stops.size() - 2);
            float localT = scaledT - stopIndex;
            colors[i] = interpolateColor(stops.get(stopIndex), stops.get(stopIndex + 1), localT);
        }

        return colors;
    }

    static Color[] computeRainbowColors(int length, float phase) {
        Color[] colors = new Color[length];
        if (length == 0) {
            return colors;
        }

        for (int i = 0; i < length; i++) {
            float hue = ((float) i / length + phase) % 1.0f;
            if (hue < 0) hue += 1.0f;
            colors[i] = Color.getHSBColor(hue, 1.0f, 1.0f);
        }

        return colors;
    }

    private static Color interpolateColor(Color a, Color b, float t) {
        int r = Math.round(a.getRed() + (b.getRed() - a.getRed()) * t);
        int g = Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t);
        int bl = Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t);
        return new Color(
            Math.clamp(r, 0, 255),
            Math.clamp(g, 0, 255),
            Math.clamp(bl, 0, 255)
        );
    }

    private static Map<String, ChatFormat> buildNamedColorMap() {
        Map<String, ChatFormat> map = new java.util.HashMap<>();
        for (ChatFormat format : ChatFormat.VALUES) {
            if (format.isColor()) {
                map.put(format.name().toLowerCase(Locale.ROOT), format);
            }
        }
        return Map.copyOf(map);
    }

    private enum Decoration {
        BOLD, ITALIC, UNDERLINED, STRIKETHROUGH, OBFUSCATED
    }

    private static class FormatState {
        Color color;
        boolean bold, italic, underlined, strikethrough, obfuscated;

        FormatState copy() {
            FormatState copy = new FormatState();
            copy.color = this.color;
            copy.bold = this.bold;
            copy.italic = this.italic;
            copy.underlined = this.underlined;
            copy.strikethrough = this.strikethrough;
            copy.obfuscated = this.obfuscated;
            return copy;
        }
    }

    private static class DeferredTag {
        final String tagName;
        final String[] args;
        final FormatState stateBefore;
        int startSegmentIndex;

        DeferredTag(String tagName, String[] args, FormatState stateBefore) {
            this.tagName = tagName;
            this.args = args;
            this.stateBefore = stateBefore;
        }
    }
}
