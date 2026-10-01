package net.aerh.imagegenerator.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The resource pack formats a pack declares support for in its {@code pack.mcmeta}, inclusive.
 * Older packs declare a single {@code pack_format}; newer ones declare {@code min_format} and
 * {@code max_format}, each either a plain integer or a {@code [major, minor]} array, in which case
 * only the major version is compared.
 *
 * @param min The lowest supported major format
 * @param max The highest supported major format
 */
@Slf4j
public record PackFormatRange(int min, int max) {

    private static final String MCMETA_PATH = "pack.mcmeta";

    public PackFormatRange {
        if (min > max) {
            throw new IllegalArgumentException("min format " + min + " is above max format " + max);
        }
    }

    public boolean contains(int format) {
        return format >= min && format <= max;
    }

    /**
     * Reads the declared range from a pack's {@code pack.mcmeta}. Prefers {@code min_format} and
     * {@code max_format} when both are present, otherwise uses {@code pack_format} as a single
     * value range.
     *
     * @return empty when the file is missing, unreadable, malformed, or declares no usable format
     */
    public static Optional<PackFormatRange> read(PackSource source) {
        if (!source.exists(MCMETA_PATH)) {
            return Optional.empty();
        }

        try {
            JsonObject root = JsonParser.parseString(new String(source.read(MCMETA_PATH), StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject pack = root.getAsJsonObject("pack");

            if (pack == null) {
                return Optional.empty();
            }

            OptionalInt min = majorVersion(pack.get("min_format"));
            OptionalInt max = majorVersion(pack.get("max_format"));

            if (min.isPresent() && max.isPresent()) {
                return Optional.of(new PackFormatRange(min.getAsInt(), max.getAsInt()));
            }

            OptionalInt single = majorVersion(pack.get("pack_format"));
            return single.isPresent() ? Optional.of(new PackFormatRange(single.getAsInt(), single.getAsInt())) : Optional.empty();
        } catch (RuntimeException e) {
            log.warn("Could not read a pack format from {}: {}", MCMETA_PATH, e.getMessage());
            return Optional.empty();
        }
    }

    private static OptionalInt majorVersion(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return OptionalInt.empty();
        }

        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            return OptionalInt.of(element.getAsInt());
        }

        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (!array.isEmpty() && array.get(0).isJsonPrimitive() && array.get(0).getAsJsonPrimitive().isNumber()) {
                return OptionalInt.of(array.get(0).getAsInt());
            }
        }

        return OptionalInt.empty();
    }
}
