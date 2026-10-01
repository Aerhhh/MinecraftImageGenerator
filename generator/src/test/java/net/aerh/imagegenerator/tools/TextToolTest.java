package net.aerh.imagegenerator.tools;

import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.exception.GeneratorValidationException;
import net.aerh.imagegenerator.item.GeneratedObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextToolTest {

    @Test
    void defaultsMatchTheDiscordCommand() {
        TextRequest request = TextRequest.builder().text("hello").build();

        assertEquals("hello", request.text());
        assertFalse(request.centered());
        assertEquals(0, request.alpha());
        assertEquals(0, request.padding());
        assertEquals(108, request.maxLineLength());
        assertFalse(request.renderBorder());
        assertNull(request.packId());
        assertNull(request.tooltipStyle());
    }

    @Test
    void textIsRequired() {
        assertThrows(GeneratorValidationException.class, () -> TextRequest.builder().build());
    }

    @Test
    void tooltipStyleWithoutBorderIsRejected() {
        TextRequest request = TextRequest.builder().text("hello").tooltipStyle("x:y").build();

        GeneratorException exception = assertThrows(GeneratorException.class,
            () -> new TextTool(ToolTestSupport.vanillaService()).render(request, null));

        assertEquals("The tooltip_style option only renders with the border; set render_border: true too!", exception.getMessage());
    }

    @Test
    void rendersPlainText() throws IOException {
        GeneratedObject result = new TextTool(ToolTestSupport.vanillaService())
            .render(TextRequest.builder().text("&aHello\\n&7World").build(), null);

        assertFalse(result.isAnimated());
        assertNotNull(result.getImage());
    }

    @Test
    void blankTooltipStyleBecomesNull() {
        assertNull(TextRequest.builder().text("hello").tooltipStyle("  ").build().tooltipStyle());
    }
}
