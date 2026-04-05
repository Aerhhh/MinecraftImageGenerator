package net.aerh.jigsaw.core.nbt;

import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.core.nbt.handler.ComponentsNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.DefaultNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.PostFlatteningNbtFormatHandler;
import net.aerh.jigsaw.core.nbt.handler.PreFlatteningNbtFormatHandler;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.exception.UnsupportedFormatException;
import net.aerh.jigsaw.spi.NbtFormatHandler;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultNbtParserTest {

    private static final NbtFormatHandlerContext EMPTY_CONTEXT = new NbtFormatHandlerContext() {
        @Override
        public <T> Optional<net.aerh.jigsaw.api.data.DataRegistry<T>> registry(
                net.aerh.jigsaw.api.data.RegistryKey<T> key) {
            return Optional.empty();
        }
    };

    // --- null / blank input ---

    @Test
    void parse_nullInput_throwsParseException() {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(new DefaultNbtFormatHandler()));

        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void parse_blankInput_throwsParseException() {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(new DefaultNbtFormatHandler()));

        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(ParseException.class);
    }

    // --- no handlers match ---

    @Test
    void parse_noMatchingHandler_throwsUnsupportedFormatException() {
        // Handler that never matches
        NbtFormatHandler neverHandles = new NbtFormatHandler() {
            @Override public String id() { return "never"; }
            @Override public int priority() { return 1; }
            @Override public boolean canHandle(String input) { return false; }
            @Override public ParsedItem parse(String input, NbtFormatHandlerContext context) {
                throw new AssertionError("Should not be called");
            }
        };

        DefaultNbtParser parser = new DefaultNbtParser(List.of(neverHandles));

        assertThatThrownBy(() -> parser.parse("{\"id\":\"minecraft:stone\"}"))
                .isInstanceOf(UnsupportedFormatException.class);
    }

    // --- handler selection by priority ---

    @Test
    void parse_handlersEvaluatedByAscendingPriority() throws ParseException {
        // Two always-accepting handlers; lower priority number wins
        List<ParsedItem[]> callOrder = new java.util.ArrayList<>();

        NbtFormatHandler lowPriority = new NbtFormatHandler() {
            @Override public String id() { return "low"; }
            @Override public int priority() { return 1; }
            @Override public boolean canHandle(String input) { return true; }
            @Override public ParsedItem parse(String input, NbtFormatHandlerContext context) {
                callOrder.add(new ParsedItem[]{null});
                return new ParsedItem("low", false, Optional.empty(), List.of(), Optional.empty(), Optional.empty());
            }
        };

        NbtFormatHandler highPriority = new NbtFormatHandler() {
            @Override public String id() { return "high"; }
            @Override public int priority() { return 2; }
            @Override public boolean canHandle(String input) { return true; }
            @Override public ParsedItem parse(String input, NbtFormatHandlerContext context) {
                return new ParsedItem("high", false, Optional.empty(), List.of(), Optional.empty(), Optional.empty());
            }
        };

        // Give high-priority (larger number) last in list but low number should win
        DefaultNbtParser parser = new DefaultNbtParser(List.of(highPriority, lowPriority));
        ParsedItem result = parser.parse("{\"id\":\"minecraft:stone\"}");

        // priority=1 handler should have been selected
        assertThat(result.itemId()).isEqualTo("low");
    }

    // --- delegates to ComponentsNbtFormatHandler ---

    @Test
    void parse_componentsFormat_usesComponentsHandler() throws ParseException {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(
                new ComponentsNbtFormatHandler(),
                new PostFlatteningNbtFormatHandler(),
                new PreFlatteningNbtFormatHandler(),
                new DefaultNbtFormatHandler()
        ));

        String input = "{\"id\":\"minecraft:diamond\",\"components\":{}}";
        ParsedItem item = parser.parse(input);

        assertThat(item.itemId()).isEqualTo("minecraft:diamond");
    }

    // --- delegates to PostFlatteningNbtFormatHandler ---

    @Test
    void parse_tagFormat_usesPostFlatteningHandler() throws ParseException {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(
                new ComponentsNbtFormatHandler(),
                new PostFlatteningNbtFormatHandler(),
                new PreFlatteningNbtFormatHandler(),
                new DefaultNbtFormatHandler()
        ));

        String input = "{\"id\":\"minecraft:sword\",\"tag\":{\"Enchantments\":[]}}";
        ParsedItem item = parser.parse(input);

        assertThat(item.itemId()).isEqualTo("minecraft:sword");
        assertThat(item.enchanted()).isFalse();
    }

    // --- SNBT normalization ---

    @Test
    void parse_snbtInput_normalizedAndParsed() throws ParseException {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(
                new ComponentsNbtFormatHandler(),
                new DefaultNbtFormatHandler()
        ));

        String snbt = "{id:\"minecraft:stone\",components:{}}";
        ParsedItem item = parser.parse(snbt);

        assertThat(item.itemId()).isEqualTo("minecraft:stone");
    }

    // --- fallback to default handler ---

    @Test
    void parse_unknownFormat_fallsBackToDefaultHandler() throws ParseException {
        DefaultNbtParser parser = new DefaultNbtParser(List.of(new DefaultNbtFormatHandler()));

        String input = "{\"id\":\"minecraft:stone\"}";
        ParsedItem item = parser.parse(input);

        assertThat(item.itemId()).isEqualTo("minecraft:stone");
        assertThat(item.enchanted()).isFalse();
        assertThat(item.lore()).isEmpty();
        assertThat(item.displayName()).isEmpty();
    }
}
