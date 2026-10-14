package net.aerh.imagegenerator.tooling;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathGuardTest {

    @TempDir
    Path root;

    @Test
    void aDirectoryContainsItself() {
        assertTrue(PathGuard.contains(root.resolve("a"), root.resolve("a")));
    }

    @Test
    void aDirectoryContainsItsDescendants() {
        assertTrue(PathGuard.contains(root.resolve("a"), root.resolve("a/b/c")));
    }

    @Test
    void aDirectoryDoesNotContainASibling() {
        assertFalse(PathGuard.contains(root.resolve("a"), root.resolve("ab")));
    }

    @Test
    void aDirectoryDoesNotContainItsParent() {
        assertFalse(PathGuard.contains(root.resolve("a/b"), root.resolve("a")));
    }

    @Test
    void containmentNormalisesDotSegments() {
        assertTrue(PathGuard.contains(root.resolve("a/../a"), root.resolve("a/./b")));
    }

    @Test
    void deletingTheCurrentDirectoryIsRefused() {
        Path current = root.resolve("project");
        Optional<String> refusal = PathGuard.deletionRefusal(current, current);
        assertEquals(Optional.of(current + " contains the current directory " + current), refusal);
    }

    @Test
    void deletingAnAncestorOfTheCurrentDirectoryIsRefused() {
        Path current = root.resolve("project/sub");
        assertTrue(PathGuard.deletionRefusal(root.resolve("project"), current).isPresent());
    }

    @Test
    void deletingADirectoryInsideTheCurrentDirectoryIsAllowed() {
        Path current = root.resolve("project");
        assertEquals(Optional.empty(), PathGuard.deletionRefusal(current.resolve("build"), current));
    }

    @Test
    void relativeTargetsAreResolvedAgainstTheCurrentDirectory() {
        Path current = root.resolve("project");
        assertEquals(Optional.empty(), PathGuard.deletionRefusal(Path.of("build"), current));
        assertTrue(PathGuard.deletionRefusal(Path.of("."), current).isPresent());
        assertTrue(PathGuard.deletionRefusal(Path.of(".."), current).isPresent());
    }
}
