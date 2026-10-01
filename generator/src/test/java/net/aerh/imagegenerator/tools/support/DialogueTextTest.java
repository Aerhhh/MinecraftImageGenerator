package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Building single- and multi-NPC dialogue strings: name prefixing, index interpolation, the Abiphone
 * symbol, {@code {options: ...}} expansion, and the malformed-input errors.
 */
class DialogueTextTest {

    @Test
    void singleDialoguePrefixesEachLineWithNpcName() {
        assertEquals(
            "&e[NPC] Steve&f: Hello\n&e[NPC] Steve&f: Bye",
            DialogueText.buildSingle("Steve", "Hello\\nBye", false)
        );
    }

    @Test
    void singleDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Steve&f: &b%%ABIPHONE%%&f Hello",
            DialogueText.buildSingle("Steve", "Hello", true)
        );
    }

    @Test
    void singleDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Steve&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            DialogueText.buildSingle("Steve", "Hi {options: Yes, No}", false)
        );
    }

    @Test
    void singleDialogueRejectsOptionsBlockWithNothingAfterMarker() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildSingle("Steve", "Hello {options:", false));

        assertEquals("Malformed {options: ...} block in dialogue (line 1)! Expected format: {options: Option 1, Option 2}", exception.getMessage());
    }

    @Test
    void singleDialogueRejectsEmptyOptionsBlock() {
        assertThrows(GeneratorException.class, () -> DialogueText.buildSingle("Steve", "Hello {options:}", false));
    }

    @Test
    void singleDialogueReportsLineNumberOfMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildSingle("Steve", "Hello\\nBye {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }

    @Test
    void multiDialogueInterpolatesNpcNamesByIndex() {
        assertEquals(
            "&e[NPC] Alice&f: Hi\n&e[NPC] Bob&f: Hey",
            DialogueText.buildMulti("Alice, Bob", "0, Hi\\n1, Hey", false)
        );
    }

    @Test
    void multiDialogueIncludesAbiphoneSymbolWhenEnabled() {
        assertEquals(
            "&e[NPC] Alice&f: &b%%ABIPHONE%%&f Hi",
            DialogueText.buildMulti("Alice", "0, Hi", true)
        );
    }

    @Test
    void multiDialogueClampsTooLargeIndexToLastName() {
        assertEquals(
            "&e[NPC] Bob&f: Hi",
            DialogueText.buildMulti("Alice, Bob", "5, Hi", false)
        );
    }

    @Test
    void multiDialogueExpandsOptionsBlock() {
        assertEquals(
            "&e[NPC] Alice&f: Hi \n&eSelect an option: &f&aYes&f &aNo&f ",
            DialogueText.buildMulti("Alice, Bob", "0, Hi {options: Yes, No}", false)
        );
    }

    @Test
    void multiDialogueRejectsLineWithoutComma() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildMulti("Alice, Bob", "0", false));

        assertEquals("Each line must start with an NPC index followed by a comma (line 1)! Example: 0, Hello!", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsNonNumericIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildMulti("Alice, Bob", "abc, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: abc (line 1)", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsNegativeIndex() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildMulti("Alice, Bob", "-1, Hi", false));

        assertEquals("Invalid NPC name index found in dialogue: -1 (line 1)", exception.getMessage());
    }

    @Test
    void multiDialogueRejectsMalformedOptionsBlock() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> DialogueText.buildMulti("Alice, Bob", "0, Hi\\n1, Hey {options:", false));

        assertTrue(exception.getMessage().contains("line 2"));
    }
}
