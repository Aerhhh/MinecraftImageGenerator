package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonObject;
import net.aerh.imagegenerator.exception.NbtParseException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hostile SNBT must fail as a parse error, never as a stack overflow or a stray runtime exception. */
class SnbtParserLimitsTest {

    private static String nested(int depth) {
        return "{a:" + "[".repeat(depth) + "]".repeat(depth) + "}";
    }

    @Test
    void nestingWithinTheLimitParses() {
        JsonObject parsed = SnbtParser.parse(nested(100));
        assertTrue(parsed.has("a"));
    }

    @Test
    void excessiveNestingIsAParseError() {
        NbtParseException thrown = assertThrows(NbtParseException.class, () -> SnbtParser.parse(nested(200_000)));
        assertTrue(thrown.getMessage().contains("nested"));
        assertNull(SnbtParser.tryParse(nested(200_000)));
    }

    @Test
    void unterminatedDeepNestingIsAParseError() {
        assertThrows(NbtParseException.class, () -> SnbtParser.parse("{a:" + "[".repeat(200_000)));
    }

    @Test
    void outOfRangeTypedNumberStaysText() {
        JsonObject parsed = SnbtParser.parse("{a:999b,b:99999s,c:5b}");
        assertEquals("999b", parsed.get("a").getAsString());
        assertEquals("99999s", parsed.get("b").getAsString());
        assertEquals(5, parsed.get("c").getAsInt());
    }
}
