package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.api.generator.GenerationContext;
import net.aerh.jigsaw.api.generator.Generator;
import net.aerh.jigsaw.api.generator.GeneratorResult;
import net.aerh.jigsaw.api.generator.RenderRequest;
import net.aerh.jigsaw.exception.RenderException;

import java.util.List;
import java.util.Objects;

/**
 * Composes multiple {@link GeneratorResult}s into a single image.
 *
 * <p>This generator accepts a {@link CompositeRequest} containing sub-requests. However,
 * since it cannot dispatch sub-requests to the appropriate generators on its own, it only
 * handles the case where the sub-request list is empty (returning a minimal image).
 *
 * <p>For full sub-request dispatch and composition, use
 * {@link net.aerh.jigsaw.api.Engine#render(RenderRequest)} with a {@link CompositeRequest}.
 * The engine handles recursive dispatch and delegates the image composition to
 * {@link ResultComposer}.
 *
 * @deprecated Use {@link net.aerh.jigsaw.api.Engine#render(RenderRequest)} instead, which
 *             handles sub-request dispatch and composition automatically.
 */
@Deprecated(since = "2.0", forRemoval = true)
public final class CompositeGenerator implements Generator<CompositeRequest, GeneratorResult> {

    /**
     * Creates a new {@link CompositeGenerator}.
     */
    public CompositeGenerator() {
    }

    /**
     * Composes results from already-rendered sub-requests.
     *
     * <p>Note: this generator cannot dispatch sub-requests. For full composite rendering,
     * use {@link net.aerh.jigsaw.api.Engine#render(RenderRequest)} instead.
     *
     * @param input   the composite request; must not be {@code null}
     * @param context the generation context; must not be {@code null}
     * @return a static image or animated image
     * @throws RenderException if composition fails
     */
    @Override
    public GeneratorResult render(CompositeRequest input, GenerationContext context) throws RenderException {
        Objects.requireNonNull(input, "input must not be null");
        Objects.requireNonNull(context, "context must not be null");

        // The CompositeGenerator can no longer render sub-requests on its own.
        // Return a minimal image for empty requests; otherwise delegate to ResultComposer
        // with an empty result list (sub-requests need the engine for dispatch).
        return ResultComposer.compose(List.of(), input.layout(), input.padding());
    }

    /**
     * Returns the input type accepted by this generator.
     *
     * @return {@link CompositeRequest}
     */
    @Override
    public Class<CompositeRequest> inputType() {
        return CompositeRequest.class;
    }

    /**
     * Returns the output type produced by this generator.
     *
     * @return {@link GeneratorResult}
     */
    @Override
    public Class<GeneratorResult> outputType() {
        return GeneratorResult.class;
    }
}
