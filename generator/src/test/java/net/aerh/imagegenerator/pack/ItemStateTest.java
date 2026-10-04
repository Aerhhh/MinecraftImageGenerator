package net.aerh.imagegenerator.pack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemStateTest {

    private static final CustomModelData DATA = new CustomModelData(List.of(1.0f), List.of(), List.of(), List.of());

    @Test
    void emptyHasNoDataAndNoDye() {
        assertEquals(CustomModelData.EMPTY, ItemState.EMPTY.customModelData());
        assertNull(ItemState.EMPTY.dyedColor());
    }

    @Test
    void ofIsUndyed() {
        ItemState state = ItemState.of(DATA);
        assertEquals(DATA, state.customModelData());
        assertNull(state.dyedColor());
        assertEquals(ItemState.EMPTY, ItemState.of(CustomModelData.EMPTY));
    }

    @Test
    void acceptsTheFullRgbRange() {
        assertEquals(0, new ItemState(CustomModelData.EMPTY, 0).dyedColor(), "black is a dye, not absent");
        assertEquals(0xFFFFFF, new ItemState(CustomModelData.EMPTY, 0xFFFFFF).dyedColor());
    }

    @Test
    void rejectsValuesOutsideRgb() {
        IllegalArgumentException negative = assertThrows(IllegalArgumentException.class,
            () -> new ItemState(CustomModelData.EMPTY, -1));
        assertTrue(negative.getMessage().contains("0xffffffff"), negative.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new ItemState(CustomModelData.EMPTY, 0x1000000));
        assertThrows(IllegalArgumentException.class, () -> new ItemState(CustomModelData.EMPTY, 0xFF3366FF),
            "an ARGB value with alpha is not a packed RGB dye");
    }

    @Test
    void requiresCustomModelData() {
        assertThrows(NullPointerException.class, () -> new ItemState(null, 0x123456));
        assertThrows(NullPointerException.class, () -> ItemState.of(null));
    }

    @Test
    void dyedColorTakesPartInEquality() {
        // Render caches key on the state, so a dyed and an undyed render must never collide.
        assertNotEquals(ItemState.EMPTY, new ItemState(CustomModelData.EMPTY, 0x123456));
        assertNotEquals(new ItemState(CustomModelData.EMPTY, 0x123456), new ItemState(CustomModelData.EMPTY, 0x654321));
        assertEquals(new ItemState(DATA, 0x123456), new ItemState(DATA, 0x123456));
    }
}
