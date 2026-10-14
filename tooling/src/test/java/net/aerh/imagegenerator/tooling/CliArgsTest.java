package net.aerh.imagegenerator.tooling;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliArgsTest {

    @Test
    void valueIsEmptyWhenTheFlagIsAbsent() {
        assertEquals(Optional.empty(), CliArgs.value(new String[]{"--other=1"}, "--padding"));
    }

    @Test
    void valueIsTheTextAfterTheFirstEqualsSign() {
        assertEquals(Optional.of("a=b"), CliArgs.value(new String[]{"--input=a=b"}, "--input"));
    }

    @Test
    void lastOccurrenceOfAFlagWins() {
        assertEquals(Optional.of("2"), CliArgs.value(new String[]{"--padding=1", "--padding=2"}, "--padding"));
    }

    @Test
    void valueDoesNotMatchAFlagThatMerelyStartsWithTheName() {
        assertEquals(Optional.empty(), CliArgs.value(new String[]{"--atlas-width-max=1"}, "--atlas-width"));
    }

    @Test
    void intValueParsesTheFlag() {
        assertEquals(7, CliArgs.intValue(new String[]{"--padding=7"}, "--padding", 0));
    }

    @Test
    void intValueFallsBackToTheDefaultWhenAbsent() {
        assertEquals(4, CliArgs.intValue(new String[]{}, "--padding", 4));
    }

    @Test
    void intValueNamesTheFlagWhenTheValueIsNotAnInteger() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> CliArgs.intValue(new String[]{"--size=big"}, "--size", 0));
        assertEquals("--size expects an integer, got 'big'", error.getMessage());
    }

    @Test
    void intValueRejectsAnEmptyValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> CliArgs.intValue(new String[]{"--size="}, "--size", 0));
        assertEquals("--size expects an integer, got ''", error.getMessage());
    }

    @Test
    void pathValueReturnsThePath() {
        assertEquals(Optional.of(Path.of("build/26.3")), CliArgs.pathValue(new String[]{"--work-dir=build/26.3"}, "--work-dir"));
    }

    @Test
    void pathValueIsEmptyWhenAbsent() {
        assertEquals(Optional.empty(), CliArgs.pathValue(new String[]{}, "--work-dir"));
    }

    @Test
    void pathValueRejectsABlankValue() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> CliArgs.pathValue(new String[]{"--work-dir=  "}, "--work-dir"));
        assertEquals("--work-dir must not be empty", error.getMessage());
    }

    @Test
    void hasFlagMatchesTheWholeArgumentOnly() {
        assertTrue(CliArgs.hasFlag(new String[]{"--clean"}, "--clean"));
        assertFalse(CliArgs.hasFlag(new String[]{"--cleanup"}, "--clean"));
        assertFalse(CliArgs.hasFlag(new String[]{"--clean=true"}, "--clean"));
    }
}
