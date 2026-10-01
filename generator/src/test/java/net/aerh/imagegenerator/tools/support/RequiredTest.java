package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RequiredTest {

    @Test
    void returnsTheValueWhenPresent() {
        String value = "x";

        assertSame(value, Required.check(value, "item_name"));
    }

    @Test
    void namesTheOptionWhenMissing() {
        GeneratorValidationException exception = assertThrows(GeneratorValidationException.class,
            () -> Required.check(null, "item_name"));

        assertEquals("item_name is required", exception.getMessage());
    }
}
