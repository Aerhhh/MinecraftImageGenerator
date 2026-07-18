package net.aerh.imagegenerator.pack;

import net.aerh.imagegenerator.pack.font.MovementTintRule;
import net.aerh.imagegenerator.testsupport.FixturePacks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shader chain gate: the movement tint rule is derived only when every chain file is present,
 * and the marker green and blue table are parsed from the shader's own GLSL rather than hardcoded.
 */
class MovementShaderDetectorTest {

    @TempDir
    Path packDir;

    private PackSource source() {
        return PackSource.directory(packDir, PackLimits.fromSystemProperties());
    }

    @Test
    void parsesMarkerAndBlueTableFromTheShaderGlsl() throws IOException {
        FixturePacks.movementShaderChain(packDir, 235, 0, 72, 4);
        MovementTintRule rule = MovementShaderDetector.detect(source()).orElseThrow();
        assertEquals(235, rule.markerGreen(), "marker green read from the include, not hardcoded");
        assertEquals(19, rule.markerBlues().size(), "one blue per config entry from 0 to 72 by 4");
        assertTrue(rule.markerBlues().contains(0));
        assertTrue(rule.markerBlues().contains(28));
        assertTrue(rule.markerBlues().contains(72));
        assertFalse(rule.markerBlues().contains(30), "an undeclared blue is not a marker");
    }

    @Test
    void parsesNonDefaultMarkerValues() throws IOException {
        FixturePacks.movementShaderChain(packDir, 200, 0, 8, 4);
        MovementTintRule rule = MovementShaderDetector.detect(source()).orElseThrow();
        assertEquals(200, rule.markerGreen());
        assertEquals(java.util.Set.of(0, 4, 8), rule.markerBlues());
    }

    @Test
    void emptyWhenAChainFileIsMissing() throws IOException {
        FixturePacks.movementShaderChain(packDir, 235, 0, 72, 4);
        Files.delete(packDir.resolve("assets/minecraft/shaders/include/config/movement.glsl"));
        assertEquals(Optional.empty(), MovementShaderDetector.detect(source()),
            "a pack lacking any chain file expects vanilla-style tinting");
    }

    @Test
    void emptyWhenNoShaderShipsAtAll() {
        assertEquals(Optional.empty(), MovementShaderDetector.detect(source()));
    }

    @Test
    void emptyWhenChainPresentButMarkerConstAbsent() throws IOException {
        FixturePacks.movementShaderChain(packDir, 235, 0, 72, 4);
        Files.writeString(packDir.resolve("assets/minecraft/shaders/include/movement.glsl"),
            "#version 150\n// no marker declared here\n");
        assertEquals(Optional.empty(), MovementShaderDetector.detect(source()));
    }

    @Test
    void emptyWhenChainPresentButNoEntries() throws IOException {
        FixturePacks.movementShaderChain(packDir, 235, 0, 72, 4);
        Files.writeString(packDir.resolve("assets/minecraft/shaders/include/config/movement.glsl"),
            "#version 150\n// no MOVEMENT entries\n");
        assertEquals(Optional.empty(), MovementShaderDetector.detect(source()));
    }
}
