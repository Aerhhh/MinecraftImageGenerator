package net.aerh.imagegenerator.text.segment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import net.aerh.imagegenerator.builder.ClassBuilder;
import net.aerh.imagegenerator.text.TagTextParser;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@ToString
public final class LineSegment {

    private final @NotNull List<ColorSegment> segments;

    public static @NotNull Builder builder() {
        return new Builder();
    }

    /**
     * Parses formatted text into line segments. Auto-detects angle-bracket tags and
     * routes to the appropriate parser. Falls back to legacy ampersand/section symbol parsing.
     */
    public static @NotNull List<LineSegment> fromLegacy(@NotNull String legacyText, char symbolSubstitute) {
        if (TagTextParser.containsTags(legacyText)) {
            return splitLines(legacyText).stream()
                .map(TagTextParser::parseLine)
                .toList();
        }

        return splitLines(legacyText).stream()
            .map(line -> TextSegment.fromLegacy(line, symbolSubstitute))
            .toList();
    }

    /**
     * Parses a tag-formatted string into line segments.
     */
    public static @NotNull List<LineSegment> fromTagFormat(@NotNull String tagText) {
        return splitLines(tagText).stream()
            .map(TagTextParser::parseLine)
            .toList();
    }

    private static @NotNull List<String> splitLines(@NotNull String text) {
        String[] parts = text.split("(?:\n|\\\\n)+");
        if (parts.length == 0) {
            return List.of("");
        }
        return List.of(parts);
    }

    public int length() {
        return this.getSegments()
            .stream()
            .mapToInt(colorSegment -> colorSegment.getText().length())
            .sum();
    }

    public @NotNull JsonElement toJson() {
        JsonArray rootArray = new JsonArray();
        rootArray.add("");
        this.getSegments().forEach(segment -> rootArray.add(segment.toJson()));
        return rootArray;
    }

    public static class Builder implements ClassBuilder<LineSegment> {

        private final List<ColorSegment> segments = new CopyOnWriteArrayList<>();

        public Builder withSegments(@NotNull ColorSegment... segments) {
            return this.withSegments(Arrays.asList(segments));
        }

        public Builder withSegments(@NotNull Iterable<ColorSegment> segments) {
            segments.forEach(this.segments::add);
            return this;
        }

        @Override
        public @NotNull LineSegment build() {
            return new LineSegment(this.segments.stream().toList());
        }
    }
}
