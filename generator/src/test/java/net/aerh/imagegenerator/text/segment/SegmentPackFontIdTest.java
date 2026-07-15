package net.aerh.imagegenerator.text.segment;

import com.google.gson.JsonObject;
import net.aerh.imagegenerator.text.MinecraftFont;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Open font ids on segments: {@link TextSegment#fromJson} keeps unknown font resource locations
 * raw instead of collapsing them to {@link MinecraftFont#DEFAULT}, built-in locations still map
 * to their enum, and the pack font id round-trips through {@code toJson}.
 */
class SegmentPackFontIdTest {

    @Test
    void unknownFontResourceLocationIsKeptRaw() {
        TextSegment segment = TextSegment.fromJson("{\"text\":\"hi\",\"font\":\"wynn:chat\"}");
        assertEquals("wynn:chat", segment.getPackFontId());
        assertEquals(MinecraftFont.DEFAULT, segment.getFont(), "built-in fallback font stays DEFAULT");
    }

    @Test
    void builtInFontResourceLocationsStillMapToTheEnum() {
        TextSegment alt = TextSegment.fromJson("{\"text\":\"hi\",\"font\":\"minecraft:alt\"}");
        assertEquals(MinecraftFont.GALACTIC, alt.getFont());
        assertNull(alt.getPackFontId());

        TextSegment bareAlt = TextSegment.fromJson("{\"text\":\"hi\",\"font\":\"alt\"}");
        assertEquals(MinecraftFont.GALACTIC, bareAlt.getFont());
        assertNull(bareAlt.getPackFontId());

        TextSegment plain = TextSegment.fromJson("{\"text\":\"hi\",\"font\":\"minecraft:default\"}");
        assertEquals(MinecraftFont.DEFAULT, plain.getFont());
        assertNull(plain.getPackFontId());
    }

    @Test
    void packFontIdRoundTripsThroughJson() {
        ColorSegment segment = ColorSegment.builder()
            .withText("x")
            .withPackFontId("wynn:chest_tooltip")
            .build();
        JsonObject json = segment.toJson();
        assertEquals("wynn:chest_tooltip", json.get("font").getAsString());

        TextSegment reparsed = TextSegment.fromJson(json);
        assertEquals("wynn:chest_tooltip", reparsed.getPackFontId());
    }

    @Test
    void builtInFontStillSerializesItsResourceLocation() {
        ColorSegment segment = ColorSegment.builder()
            .withText("x")
            .withFont(MinecraftFont.ILLAGERALT)
            .build();
        assertEquals("minecraft:illageralt", segment.toJson().get("font").getAsString());
    }

    @Test
    void fromResourceLocationOrNullDistinguishesUnknownFromDefault() {
        assertEquals(MinecraftFont.DEFAULT, MinecraftFont.fromResourceLocationOrNull(null));
        assertEquals(MinecraftFont.DEFAULT, MinecraftFont.fromResourceLocationOrNull(""));
        assertEquals(MinecraftFont.DEFAULT, MinecraftFont.fromResourceLocationOrNull("minecraft:default"));
        assertNull(MinecraftFont.fromResourceLocationOrNull("wynn:chat"));
        assertEquals(MinecraftFont.DEFAULT, MinecraftFont.fromResourceLocation("wynn:chat"),
            "the non-null overload keeps its historical collapse-to-default contract");
    }
}
