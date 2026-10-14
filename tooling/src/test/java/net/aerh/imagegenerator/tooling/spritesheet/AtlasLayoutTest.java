package net.aerh.imagegenerator.tooling.spritesheet;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasLayoutTest {

    @Test
    void defaultLayoutMatchesTheBundledAtlas() {
        assertEquals(256, AtlasLayout.DEFAULT.tileSize());
        assertEquals(4096, AtlasLayout.DEFAULT.maxWidth());
        assertEquals(AtlasLayout.UNLIMITED, AtlasLayout.DEFAULT.maxHeight());
        assertEquals(0, AtlasLayout.DEFAULT.padding());
        assertEquals(Integer.MAX_VALUE, AtlasLayout.UNLIMITED);
    }

    @Test
    void pitchIsTilePlusPaddingOnBothSides() {
        assertEquals(10, new AtlasLayout(8, 100, 100, 1).pitch());
        assertEquals(8, new AtlasLayout(8, 100, 100, 0).pitch());
    }

    @Test
    void columnsAndRowsRoundDownWhenBoundsAreNotAMultipleOfPitch() {
        AtlasLayout layout = new AtlasLayout(8, 35, 21, 1);
        assertEquals(3, layout.columns());
        assertEquals(2, layout.rowsPerPage());
        assertEquals(6, layout.capacity());
    }

    @Test
    void unlimitedHeightGivesUnlimitedRowsWithoutOverflow() {
        AtlasLayout layout = new AtlasLayout(1, 4096, AtlasLayout.UNLIMITED, 0);
        assertEquals(Integer.MAX_VALUE, layout.rowsPerPage());
        assertEquals(Integer.MAX_VALUE, layout.capacity());
    }

    @Test
    void zeroTileSizeIsRejectedWithTheOffendingValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new AtlasLayout(0, 8, 8, 0));
        assertEquals("Tile size must be positive, got 0", error.getMessage());
    }

    @Test
    void negativePaddingIsRejectedWithTheOffendingValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new AtlasLayout(4, 8, 8, -1));
        assertEquals("Padding must be non-negative, got -1", error.getMessage());
    }

    @Test
    void paddingThatOverflowsThePitchIsRejectedAsTooLarge() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new AtlasLayout(Integer.MAX_VALUE, 8, 8, 1));
        assertEquals("Padding 1 is too large for tile size " + Integer.MAX_VALUE, error.getMessage());
    }

    @Test
    void boundsSmallerThanOnePaddedTileAreRejected() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> new AtlasLayout(4, 5, 5, 1));
        assertEquals("Atlas bounds 5x5 must fit at least one padded tile of 6px", error.getMessage());
    }

    @Test
    void parsingWithoutFlagsReturnsTheGivenDefaults() {
        AtlasLayout defaults = new AtlasLayout(16, 64, 64, 2);
        assertEquals(defaults, AtlasLayout.fromArgs(new String[]{"--input=x", "--debug"}, defaults));
    }

    @Test
    void parsingReadsEveryLayoutFlag() {
        String[] args = {"--tile-size=32", "--atlas-width=2048", "--atlas-height=1024", "--padding=3"};
        assertEquals(new AtlasLayout(32, 2048, 1024, 3), AtlasLayout.fromArgs(args, AtlasLayout.DEFAULT));
    }

    @Test
    void parsingOverridesOnlyTheFlagsThatArePresent() {
        AtlasLayout layout = AtlasLayout.fromArgs(new String[]{"--padding=1"}, AtlasLayout.DEFAULT);
        assertEquals(new AtlasLayout(256, 4096, AtlasLayout.UNLIMITED, 1), layout);
    }

    @Test
    void parsingNamesTheFlagWhenTheValueIsNotAnInteger() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AtlasLayout.fromArgs(new String[]{"--padding=x"}, AtlasLayout.DEFAULT));
        assertEquals("--padding expects an integer, got 'x'", error.getMessage());
    }

    @Test
    void parsingRejectsAnEmptyValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AtlasLayout.fromArgs(new String[]{"--atlas-width="}, AtlasLayout.DEFAULT));
        assertEquals("--atlas-width expects an integer, got ''", error.getMessage());
    }

    @Test
    void parsingStillValidatesTheCombinedLayout() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> AtlasLayout.fromArgs(new String[]{"--atlas-width=10"}, AtlasLayout.DEFAULT));
        assertTrue(error.getMessage().startsWith("Atlas bounds 10x"), error.getMessage());
    }
}
