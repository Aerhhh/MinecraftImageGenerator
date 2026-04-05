package net.aerh.jigsaw.api.generator;

import net.aerh.jigsaw.exception.RenderException;

/**
 * Core rendering contract that converts a typed input into a typed output.
 *
 * <p>Generators are the primary extension point for adding new rendering strategies to Jigsaw.
 * Each generator is responsible for a single logical rendering task (e.g. rendering a single item,
 * rendering an inventory grid, or rendering a player head). Generators are contributed via the
 * {@link net.aerh.jigsaw.spi.GeneratorFactory} SPI.
 *
 * <p>Example of a custom generator:
 *
 * <pre>{@code
 * public class MyGenerator implements Generator<MyRequest, GeneratorResult> {
 *
 *     public GeneratorResult render(MyRequest input, GenerationContext context) throws RenderException {
 *         // produce and return a GeneratorResult
 *     }
 *
 *     public Class<MyRequest> inputType()  { return MyRequest.class; }
 *     public Class<GeneratorResult> outputType() { return GeneratorResult.class; }
 * }
 * }</pre>
 *
 * @param <I> the input request type
 * @param <O> the output result type
 *
 * @see GeneratorResult
 * @see net.aerh.jigsaw.spi.GeneratorFactory
 */
public interface Generator<I, O> {

    /**
     * Renders the given input using the supplied context and returns the result.
     *
     * @param input   the rendering request; must not be {@code null}
     * @param context caller-supplied options controlling this render; must not be {@code null}
     * @return the rendering result; never {@code null}
     * @throws RenderException if rendering fails due to missing resources or invalid state
     */
    O render(I input, GenerationContext context) throws RenderException;

    /**
     * Returns the class of the input type accepted by this generator.
     *
     * @return the input type class
     */
    Class<I> inputType();

    /**
     * Returns the class of the output type produced by this generator.
     *
     * @return the output type class
     */
    Class<O> outputType();
}
