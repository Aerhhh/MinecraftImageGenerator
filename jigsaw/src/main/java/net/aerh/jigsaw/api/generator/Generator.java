package net.aerh.jigsaw.api.generator;

import net.aerh.jigsaw.exception.RenderException;

public interface Generator<I, O> {

    O render(I input, GenerationContext context) throws RenderException;

    Class<I> inputType();

    Class<O> outputType();
}
