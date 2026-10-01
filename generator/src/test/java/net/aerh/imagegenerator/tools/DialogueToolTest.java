package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueToolTest {

    @Test
    void defaultsMatchTheDiscordCommand() {
        DialogueRequest request = DialogueRequest.single("Steve", "Hello").build();

        assertEquals(DialogueRequest.Mode.SINGLE, request.mode());
        assertEquals("Steve", request.npcNames());
        assertEquals("Hello", request.dialogue());
        assertEquals(91, request.maxLineLength());
        assertFalse(request.abiphone());
        assertNull(request.skinValue());
        assertFalse(request.renderBackground());
        assertNull(request.packId());
    }

    @Test
    void multiModeCarriesTheNameList() {
        DialogueRequest request = DialogueRequest.multi("Steve, Alex", "0, Hi\\n1, Hey").build();

        assertEquals(DialogueRequest.Mode.MULTI, request.mode());
        assertEquals("Steve, Alex", request.npcNames());
    }

    @Test
    void requiredFieldsAreEnforced() {
        assertThrows(GeneratorValidationException.class, () -> DialogueRequest.single(null, "x").build());
        assertThrows(GeneratorValidationException.class, () -> DialogueRequest.single("x", null).build());
    }

    @Test
    void rendersSingleDialogue() throws IOException {
        GeneratedObject result = new DialogueTool(ToolTestSupport.vanillaService())
            .render(DialogueRequest.single("Steve", "Hello there\\nBye {options: Yes, No}").build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
    }

    @Test
    void rendersMultiDialogue() throws IOException {
        GeneratedObject result = new DialogueTool(ToolTestSupport.vanillaService())
            .render(DialogueRequest.multi("Steve, Alex", "0, Hi\\n1, Hey").build(), null);

        assertNotNull(result.getImage());
    }

    @Test
    @Tag("network")
    void skinValueAddsAHeadInFront() throws IOException {
        DialogueTool tool = new DialogueTool(ToolTestSupport.vanillaService());

        GeneratedObject plain = tool.render(DialogueRequest.single("Steve", "Hi").build(), null);
        GeneratedObject withHead = tool.render(DialogueRequest.single("Steve", "Hi").skinValue("Aerh").build(), null);

        assertTrue(withHead.getImage().getWidth() > plain.getImage().getWidth());
    }

    @Test
    void multiDialogueErrorsSurfaceVerbatim() {
        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new DialogueTool(ToolTestSupport.vanillaService())
                .render(DialogueRequest.multi("Steve", "no index here").build(), null));

        assertEquals("Each line must start with an NPC index followed by a comma (line 1)! Example: 0, Hello!", exception.getMessage());
    }

    @Test
    void maxLineLengthAboveTheLibraryCeilingIsClamped() throws IOException {
        DialogueTool tool = new DialogueTool(ToolTestSupport.vanillaService());
        String longLine = "word ".repeat(60);

        GeneratedObject beyondCeiling = tool.render(DialogueRequest.single("Steve", longLine).maxLineLength(300).build(), null);
        GeneratedObject atCeiling = tool.render(DialogueRequest.single("Steve", longLine).maxLineLength(128).build(), null);
        GeneratedObject narrow = tool.render(DialogueRequest.single("Steve", longLine).maxLineLength(91).build(), null);

        assertEquals(atCeiling.getImage().getWidth(), beyondCeiling.getImage().getWidth(),
            "a line length above the library ceiling is clamped to it");
        assertTrue(beyondCeiling.getImage().getWidth() > narrow.getImage().getWidth(),
            "the ceiling is still wider than the default line length");
    }

    @Test
    void blankSkinValueBecomesNull() {
        assertNull(DialogueRequest.single("Steve", "Hi").skinValue(" ").build().skinValue());
    }
}
