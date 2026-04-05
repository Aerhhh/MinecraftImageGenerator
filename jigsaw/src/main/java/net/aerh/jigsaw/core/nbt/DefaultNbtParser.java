package net.aerh.jigsaw.core.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.api.nbt.NbtParser;
import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.UnsupportedFormatException;
import net.aerh.jigsaw.spi.NbtFormatHandler;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default {@link NbtParser} that orchestrates a list of {@link NbtFormatHandler}s.
 * <p>
 * Handlers are sorted by ascending {@link NbtFormatHandler#priority()} at construction time.
 * For each {@link #parse(String)} call the input is first normalized to JSON (trying JSON
 * parse first, then SNBT), then the sorted handler list is iterated until the first handler
 * for which {@link NbtFormatHandler#canHandle(String)} returns {@code true} is used.
 * <p>
 * Throws {@link UnsupportedFormatException} if no handler accepts the input.
 */
public final class DefaultNbtParser implements NbtParser {

    private final List<NbtFormatHandler> handlers;
    private final NbtFormatHandlerContext context;

    /**
     * Constructs a parser with the given handlers and an empty context.
     *
     * @param handlers The handlers to use, sorted by ascending priority (lower number = evaluated first).
     */
    public DefaultNbtParser(List<NbtFormatHandler> handlers) {
        this(handlers, emptyContext());
    }

    /**
     * Constructs a parser with the given handlers and context.
     *
     * @param handlers The handlers to use.
     * @param context  The context passed to each handler during parsing.
     */
    public DefaultNbtParser(List<NbtFormatHandler> handlers, NbtFormatHandlerContext context) {
        this.handlers = handlers.stream()
                .sorted(Comparator.comparingInt(NbtFormatHandler::priority))
                .toList();
        this.context = context;
    }

    @Override
    public ParsedItem parse(String input) throws ParseException {
        if (input == null || input.isBlank()) {
            throw new ParseException("NBT input must not be null or blank",
                    Map.of("input", String.valueOf(input)));
        }

        // Normalize: try as JSON first, then SNBT; work with the original string for handler dispatch
        String normalized = normalizeToJsonString(input);

        for (NbtFormatHandler handler : handlers) {
            if (handler.canHandle(normalized)) {
                return handler.parse(normalized, context);
            }
        }

        throw new UnsupportedFormatException("No handler found for NBT input",
                Map.of("input", input, "handlersChecked", handlers.size()));
    }

    /**
     * Attempts to convert the input to a canonical JSON string.
     * If the input is already valid JSON, it is returned as-is.
     * Otherwise it is parsed as SNBT and serialized to JSON.
     * If both fail, the original input string is returned.
     *
     * @param input The raw NBT input.
     * @return The normalized JSON string, or the original input if normalization fails.
     */
    private static String normalizeToJsonString(String input) {
        // Fast path: already valid JSON
        try {
            JsonElement element = JsonParser.parseString(input);
            // Return as canonical JSON string
            return element.toString();
        } catch (Exception ignored) {}

        // Try SNBT
        try {
            JsonElement element = SnbtParser.parse(input);
            return element.toString();
        } catch (Exception ignored) {}

        // Return as-is; individual handlers can decide what to do
        return input;
    }

    private static NbtFormatHandlerContext emptyContext() {
        return new NbtFormatHandlerContext() {
            @Override
            public <T> Optional<net.aerh.jigsaw.api.data.DataRegistry<T>> registry(
                    net.aerh.jigsaw.api.data.RegistryKey<T> key) {
                return Optional.empty();
            }
        };
    }
}
