package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchToolTest {

    @Test
    void findsVanillaItems() {
        SearchResult result = new SearchTool(ToolTestSupport.vanillaService()).search("diamond_sw");

        assertTrue(result.spritesheetResults().contains("diamond_sword"));
        assertTrue(result.packResults().isEmpty());
        assertFalse(result.isEmpty());
    }

    @Test
    void findsPackItemRefs() throws IOException {
        SearchResult result = new SearchTool(ToolTestSupport.fixtureService()).search("SIMPLE");

        assertTrue(result.packResults().contains("testpack:item/simple"));
    }

    @Test
    void emptyWhenNothingMatches() {
        SearchResult result = new SearchTool(ToolTestSupport.vanillaService()).search("zzzz_not_an_item_zzzz");

        assertTrue(result.isEmpty());
        assertEquals(0, result.spritesheetResults().size());
    }

    @Test
    void missingQueryIsRejected() {
        GeneratorValidationException exception = assertThrows(GeneratorValidationException.class,
            () -> new SearchTool(ToolTestSupport.vanillaService()).search(null));

        assertEquals("query is required", exception.getMessage());
    }
}
