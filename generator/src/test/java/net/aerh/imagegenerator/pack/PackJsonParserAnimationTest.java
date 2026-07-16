package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.exception.PackLoadException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pins the FULL animation mcmeta model Wave 9 added on top of the first-frame subset. */
class PackJsonParserAnimationTest {

    private static AnimationMeta parse(String json) {
        return PackJsonParser.parseAnimationMeta(json.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void framesListParsesIntAndObjectEntriesWithPerFrameTimes() {
        AnimationMeta meta = parse("""
            {"animation":{"frametime":3,"frames":[4,3,{"index":0,"time":81}]}}""");

        assertEquals(3, meta.defaultFrameTimeTicks());
        assertEquals(List.of(
            new AnimationMeta.FrameEntry(4, 3),
            new AnimationMeta.FrameEntry(3, 3),
            new AnimationMeta.FrameEntry(0, 81)), meta.frames());
        assertEquals(4, meta.firstFrameIndex());
    }

    @Test
    void absentFramesListMeansDefaultOrder() {
        AnimationMeta meta = parse("""
            {"animation":{"frametime":5}}""");
        assertNull(meta.frames(), "absent list = every flipbook frame top to bottom");
        assertEquals(5, meta.defaultFrameTimeTicks());
        assertEquals(0, meta.firstFrameIndex());
    }

    @Test
    void frametimeDefaultsToOneTick() {
        assertEquals(1, parse("{\"animation\":{}}").defaultFrameTimeTicks());
    }

    @Test
    void interpolateParses() {
        assertTrue(parse("{\"animation\":{\"interpolate\":true}}").interpolate());
        assertFalse(parse("{\"animation\":{}}").interpolate());
    }

    @Test
    void nonPositiveTimesAreRejected() {
        assertThrows(PackLoadException.class, () -> parse("{\"animation\":{\"frametime\":0}}"));
        assertThrows(PackLoadException.class,
            () -> parse("{\"animation\":{\"frames\":[{\"index\":0,\"time\":0}]}}"));
        assertThrows(PackLoadException.class,
            () -> parse("{\"animation\":{\"frames\":[{\"index\":0,\"time\":-2}]}}"));
    }

    @Test
    void everyFrameEntryIsValidatedNotJustTheFirst() {
        assertThrows(PackLoadException.class,
            () -> parse("{\"animation\":{\"frames\":[0,\"nope\"]}}"));
        assertThrows(PackLoadException.class,
            () -> parse("{\"animation\":{\"frames\":[0,{\"time\":3}]}}"));
    }

    @Test
    void nonArrayFramesIsRejected() {
        assertThrows(PackLoadException.class, () -> parse("{\"animation\":{\"frames\":7}}"));
    }
}
