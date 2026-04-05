package net.aerh.jigsaw.api.generator;

/**
 * Marker interface for all render requests that can be submitted to the
 * {@link net.aerh.jigsaw.api.Engine}.
 *
 * <p>Each concrete request type (e.g. {@link net.aerh.jigsaw.core.generator.ItemRequest},
 * {@link net.aerh.jigsaw.core.generator.TooltipRequest}) implements this interface so the
 * engine can dispatch to the appropriate generator via a single
 * {@link net.aerh.jigsaw.api.Engine#render(RenderRequest)} entry point.
 */
public interface RenderRequest {
}
