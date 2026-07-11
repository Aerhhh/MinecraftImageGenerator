package net.aerh.imagegenerator.parser.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Percents pair off as {@code %%} delimiters, so a value that ends in a single {@code %} (written
 * as a trailing {@code %%%}) keeps that percent as value content instead of it being lost to the
 * closing delimiter. See {@code Parser#VARIABLE_PATTERN}.
 */
class StatParserPercentValueTest {

    private final StatParser parser = new StatParser();

    @Test
    void trailingPercentBecomesValueContent() {
        // %%health:50%%% -> value "50%", rendered by the NORMAL format as "&c50%❤ Health".
        String result = parser.parse("%%health:50%%%");
        assertTrue(result.contains("50%❤ Health"), result);
        assertFalse(result.contains("50%%"), result);
    }

    @Test
    void adjacentStatPlaceholdersBothResolve() {
        // Regression: the %%%% seam used to be swallowed as the first stat's value, leaving the
        // second placeholder's name as literal text.
        String result = parser.parse("%%health:50%%%%strength:30%%");
        assertTrue(result.contains("50❤ Health"), result);
        assertTrue(result.contains("30❁ Strength"), result);
        assertFalse(result.contains("%%"), result);
    }

    @Test
    void trailingPercentValueButtsAgainstAnotherPlaceholder() {
        // %%health:50%%%%%zone%% -> the odd run of five percents gives one to the health value
        // (50%) and leaves %%zone%% intact. IconParser runs before StatParser in the real chain,
        // so it resolves the zone icon first; StatParser then formats health against the icon.
        String afterIcons = new IconParser().parse("%%health:50%%%%%zone%%");
        System.out.println(afterIcons);
        assertEquals("%%health:50%%%⏣", afterIcons);

        String result = parser.parse(afterIcons);
        System.out.println(result);
        assertTrue(result.contains("50%❤ Health"), result);
        assertTrue(result.contains("⏣"), result);
        assertFalse(result.contains("50%%"), result);
    }
}
