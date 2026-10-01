package net.aerh.imagegenerator.tools.support;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberFormatsTest {

    @Test
    void commaSeparatedGroupsThousands() {
        assertEquals("1,234,567", NumberFormats.COMMA_SEPARATED.format(1234567));
        assertEquals("-50", NumberFormats.COMMA_SEPARATED.format(-50));
        assertEquals("0", NumberFormats.COMMA_SEPARATED.format(0));
    }

    @Test
    void commaSeparatedRoundsDoublesToWholeNumbers() {
        assertEquals("1,235", NumberFormats.COMMA_SEPARATED.format(1234.6));
    }

    @Test
    void formatSizeUsesBinaryUnits() {
        assertEquals("0", NumberFormats.formatSize(0));
        assertEquals("0", NumberFormats.formatSize(-1));
        assertEquals("512 B", NumberFormats.formatSize(512));
        assertEquals("64 kB", NumberFormats.formatSize(64 * 1024));
        assertEquals("1.5 MB", NumberFormats.formatSize(1536 * 1024));
    }
}
