package net.aerh.jigsaw.api.text;

import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.exception.RenderException;

import java.util.List;

/**
 * Renders a list of formatted text lines into a {@link GeneratorResult}.
 */
public interface TextRenderer {

    /**
     * Renders the given lines and returns the output image.
     *
     * @param lines   The lines to render, each may contain Minecraft formatting codes.
     * @param options Rendering configuration.
     * @return The rendered output, which may be static or animated.
     * @throws RenderException if rendering fails.
     */
    GeneratorResult render(List<String> lines, TextRenderOptions options) throws RenderException;
}
