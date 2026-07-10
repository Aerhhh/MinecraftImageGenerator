package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Vanilla 1.16+ hex colors in NBT/JSON text components must survive conversion to the
 * ampersand-string intermediate instead of being silently dropped (the component would
 * previously inherit its parent's color).
 */
class NbtTextComponentHexTest {

    private static JsonObject component(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void hexColorIsEmittedAsInBandCode() {
        assertEquals("&#ff00aaHi",
            NbtTextComponentUtil.toFormattedString(component("{\"text\":\"Hi\",\"color\":\"#FF00AA\"}")));
    }

    @Test
    void extraSiblingsInheritParentHexColor() {
        String json = "{\"text\":\"A\",\"color\":\"#ff00aa\",\"extra\":["
            + "{\"text\":\"B\",\"color\":\"gold\"},"
            + "{\"text\":\"C\"}"
            + "]}";
        assertEquals("&#ff00aaA&6B&#ff00aaC", NbtTextComponentUtil.toFormattedString(component(json)));
    }

    @Test
    void invalidHexColorIsIgnoredAndInheritsParent() {
        String json = "{\"text\":\"A\",\"color\":\"#ff00aa\",\"extra\":["
            + "{\"text\":\"B\",\"color\":\"#zzzzzz\"}"
            + "]}";
        assertEquals("&#ff00aaAB", NbtTextComponentUtil.toFormattedString(component(json)));
    }

    @Test
    void parseTextValueHandlesHexComponentStrings() {
        assertEquals("&#00ff00Lore line",
            NbtTextComponentUtil.parseTextValue("{\"text\":\"Lore line\",\"color\":\"#00ff00\"}"));
    }

    @Test
    void hexColorResetsFormattingBetweenSiblings() {
        String json = "{\"text\":\"A\",\"bold\":true,\"extra\":["
            + "{\"text\":\"B\",\"color\":\"#ff00aa\",\"bold\":false}"
            + "]}";
        assertEquals("&lA&#ff00aaB", NbtTextComponentUtil.toFormattedString(component(json)));
    }
}
