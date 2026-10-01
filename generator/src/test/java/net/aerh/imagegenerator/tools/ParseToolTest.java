package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.exception.NbtParseException;
import net.aerh.imagegenerator.tools.nbt.ItemModelFallback;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseToolTest {

    private static final String VANILLA_ITEM = """
        {"id":"minecraft:diamond_sword","components":{"minecraft:custom_name":"{\\"text\\":\\"Sharp\\",\\"color\\":\\"aqua\\"}","minecraft:lore":["{\\"text\\":\\"A blade\\",\\"color\\":\\"gray\\"}"]}}""";
    private static final String MULTI_LINE_ITEM = """
        {"id":"minecraft:diamond_sword","components":{"minecraft:custom_name":"{\\"text\\":\\"Sharp\\",\\"color\\":\\"aqua\\"}","minecraft:lore":["{\\"text\\":\\"First line\\",\\"color\\":\\"gray\\"}","{\\"text\\":\\"Second line\\",\\"color\\":\\"gray\\"}"]}}""";
    private static final String UNRESOLVABLE_MODEL = """
        {"id":"minecraft:paper","components":{"minecraft:item_model":"testpack:item/never_shipped"}}""";

    @Test
    void nbtIsRequired() {
        assertThrows(GeneratorValidationException.class, () -> new ParseRequest(null, null));
    }

    @Test
    void unreadableInputBecomesAnNbtParseException() {
        NbtParseException exception = assertThrows(NbtParseException.class,
            () -> new ParseTool(ToolTestSupport.vanillaService()).render(new ParseRequest("{not json", null), null));

        // The library's parser reports unreadable text itself, so Gson's JsonParseException never escapes
        // it for text input; the tool's own "badly formatted" message is a defensive mapping only.
        assertTrue(exception.getMessage().startsWith("Input is neither valid JSON nor valid SNBT"), exception.getMessage());
    }

    @Test
    void rendersVanillaNbtAndEmitsACommand() throws IOException {
        ParseResult result = new ParseTool(ToolTestSupport.vanillaService()).render(new ParseRequest(VANILLA_ITEM, null), null);

        assertNotNull(result.image().getImage());
        assertEquals(ItemModelFallback.NONE, result.fallback());
        assertNull(result.fallbackNotice());
        assertTrue(result.slashCommand().startsWith("/gen item "), result.slashCommand());
        assertTrue(result.slashCommand().endsWith(" item_id: diamond_sword"), result.slashCommand());
    }

    @Test
    void commandStaysOneLineForMultiLineLore() throws IOException {
        ParseResult result = new ParseTool(ToolTestSupport.vanillaService()).render(new ParseRequest(MULTI_LINE_ITEM, null), null);

        assertFalse(result.slashCommand().contains("\n"), "command is one line");
        assertTrue(result.slashCommand().contains("&7First line\\n&7Second line"), result.slashCommand());
    }

    @Test
    void unresolvableModelFallsBackAndExplains() throws IOException {
        ParseResult result = new ParseTool(ToolTestSupport.fixtureService())
            .render(new ParseRequest(UNRESOLVABLE_MODEL, ToolTestSupport.FIXTURE_PACK), null);

        assertEquals(ItemModelFallback.ITEM_ID, result.fallback());
        assertEquals("The `" + ToolTestSupport.FIXTURE_PACK + "` pack could not resolve item model `testpack:item/never_shipped`,"
            + " so the base item `paper` was rendered instead. The pack is likely older than this item.", result.fallbackNotice());
        assertTrue(result.slashCommand().contains(" item_model: testpack:item/never_shipped"), result.slashCommand());
    }
}
