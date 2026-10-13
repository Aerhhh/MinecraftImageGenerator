package net.aerh.imagegenerator.tooling.minecraft;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RendererRevisionTest {
    @TempDir Path directory;

    @Test void pinnedRevisionDoesNotPullAndRebuildsWhenHeadChanges() throws Exception {
        Path repo = directory.resolve("renderer");
        Files.createDirectories(repo);
        git(repo, "init");
        git(repo, "config", "user.name", "Test");
        git(repo, "config", "user.email", "test@example.invalid");
        Files.writeString(repo.resolve("model.txt"), "old");
        git(repo, "add", "model.txt");
        git(repo, "commit", "-m", "old");
        String old = git(repo, "rev-parse", "HEAD");
        Files.writeString(repo.resolve("model.txt"), "new");
        git(repo, "commit", "-am", "new");
        var renderer = new TestRenderer(repo);
        renderer.setRevision(old);
        renderer.setup(false, false);
        assertEquals(old, git(repo, "rev-parse", "HEAD"));
        assertEquals(1, renderer.builds);
        renderer.setup(false, false);
        assertEquals(1, renderer.builds);
    }

    @Test void noUpdateCanReuseCheckoutWithoutAnyRemote() throws Exception {
        Path repo = directory.resolve("renderer");
        Files.createDirectories(repo);
        git(repo, "init");
        var renderer = new TestRenderer(repo);
        renderer.setUpdateExisting(false);
        renderer.setup(false, false);
        assertEquals(0, renderer.builds);
    }

    @Test void commitMustBeAFullSha() {
        assertThrows(IllegalArgumentException.class, () -> new ItemRenderer(directory).setRevision("master"));
    }

    private static String git(Path repo, String... args) throws Exception {
        var command = new ArrayList<String>(List.of("git"));
        command.addAll(List.of(args));
        var process = new ProcessBuilder(command).directory(repo.toFile()).redirectErrorStream(true).start();
        var output = new String(process.getInputStream().readAllBytes()).trim();
        assertEquals(0, process.waitFor(), output);
        return output;
    }

    private static class TestRenderer extends ItemRenderer {
        int builds;
        TestRenderer(Path path) { super(path); }
        protected void checkPrerequisites() {}
        public boolean isBuilt() { return true; }
        protected void buildProject() { builds++; }
    }
}
