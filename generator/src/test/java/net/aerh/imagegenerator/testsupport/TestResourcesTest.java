package net.aerh.imagegenerator.testsupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves that {@code generator/src/test/resources/} is on the test classpath and that
 * {@link TestResources} can load fixtures from it. Satisfies TEST-01 Success Criterion #3.
 */
class TestResourcesTest {

    @Test
    void readString_loadsMojangProfileFixture_returnsNonEmptyJson() {
        String json = TestResources.readString("mojang/profile-success.json");

        assertNotNull(json, "fixture should resolve from the test classpath");
        assertFalse(json.isBlank(), "fixture should contain non-blank content");
    }

    @Test
    void readString_missingResource_throwsIllegalArgumentException() {
        assertThrows(
            IllegalArgumentException.class,
            () -> TestResources.readString("does/not/exist.json")
        );
    }
}