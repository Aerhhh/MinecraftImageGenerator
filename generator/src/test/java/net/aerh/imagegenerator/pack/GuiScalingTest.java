package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GuiScalingTest {

    private static final GuiScaling.NineSlice.Border UNIFORM_NINE = new GuiScaling.NineSlice.Border(9, 9, 9, 9);

    @Test
    void tileAcceptsPositiveDimensions() {
        assertDoesNotThrow(() -> new GuiScaling.Tile(100, 100));
    }

    @Test
    void tileRejectsZeroOrNegativeDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.Tile(0, 100));
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.Tile(100, -1));
    }

    @Test
    void nineSliceAcceptsBorderExactlyFillingTexture() {
        assertDoesNotThrow(() -> new GuiScaling.NineSlice(100, 100,
            new GuiScaling.NineSlice.Border(50, 50, 50, 50), false));
    }

    @Test
    void nineSliceRejectsZeroOrNegativeDimensions() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(0, 100, UNIFORM_NINE, false));
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(100, 0, UNIFORM_NINE, false));
    }

    @Test
    void nineSliceRejectsNullBorder() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(100, 100, null, false));
    }

    @Test
    void nineSliceRejectsBordersExceedingTexture() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(100, 100,
            new GuiScaling.NineSlice.Border(51, 9, 50, 9), false));
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(100, 100,
            new GuiScaling.NineSlice.Border(9, 60, 9, 41), false));
    }

    @Test
    void borderRejectsNegativeSides() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice.Border(-1, 9, 9, 9));
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice.Border(9, 9, 9, -1));
    }

    @Test
    void borderAcceptsZeroSides() {
        assertDoesNotThrow(() -> new GuiScaling.NineSlice.Border(0, 0, 0, 0));
    }

    @Test
    void dimensionsRejectValuesBeyondHardCap() {
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.Tile(65_537, 100));
        assertThrows(IllegalArgumentException.class, () -> new GuiScaling.NineSlice(100, 65_537, UNIFORM_NINE, false));
    }
}
