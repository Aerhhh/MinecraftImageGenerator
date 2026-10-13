package net.aerh.imagegenerator.tooling.minecraft;

import net.aerh.imagegenerator.tooling.spritesheet.SpritesheetGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class ToolingFailureTest {
    @TempDir Path directory;

    @Test void missingDotNetIsAnError() {
        var tool = new MinecraftAssetTool() {
            protected boolean isDotNetAvailable() { return false; }
        };
        assertThrows(IOException.class, tool::runRenderStep);
    }

    @Test void rendererFailureIsPropagated() {
        var tool = new MinecraftAssetTool() {
            protected boolean isDotNetAvailable() { return true; }
            protected ItemRenderer createRenderer() {
                return new ItemRenderer(directory) {
                    public void setup(boolean clone, boolean rebuild) throws IOException {
                        throw new IOException("simulated renderer failure");
                    }
                };
            }
        };
        var exception = assertThrows(IOException.class, tool::runRenderStep);
        assertEquals("simulated renderer failure", exception.getCause().getMessage());
    }

    @Test void invalidPngAndEmptyDirectoryFailInsteadOfPublishingPartialAtlases() throws Exception {
        assertThrows(IllegalStateException.class, () -> new SpritesheetGenerator(directory.toString()).generate());
        Files.writeString(directory.resolve("broken.png"), "not a PNG");
        assertThrows(IllegalStateException.class, () -> new SpritesheetGenerator(directory.toString()).generate());
    }

    @Test void anEmptyBuildDirectoryIsNotACompiledRenderer() throws Exception {
        Files.createDirectories(directory.resolve("CreateAtlases/bin"));
        assertFalse(new ItemRenderer(directory).isBuilt());
    }
}
