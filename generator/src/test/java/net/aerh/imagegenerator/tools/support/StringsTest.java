package net.aerh.imagegenerator.tools.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class StringsTest {

    @Test
    void nullStaysNull() {
        assertNull(Strings.blankToNull(null));
    }

    @Test
    void emptyAndWhitespaceBecomeNull() {
        assertNull(Strings.blankToNull(""));
        assertNull(Strings.blankToNull("   "));
        assertNull(Strings.blankToNull("\t\n"));
    }

    @Test
    void contentIsReturnedUnchanged() {
        String value = " stone ";

        assertSame(value, Strings.blankToNull(value));
    }
}
