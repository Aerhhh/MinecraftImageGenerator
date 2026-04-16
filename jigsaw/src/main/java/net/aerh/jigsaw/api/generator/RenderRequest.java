package net.aerh.jigsaw.api.generator;

/**
 * Marker interface for all render requests that can be dispatched to an
 * {@link Engine}.
 *
 * @see Engine#render(RenderRequest)
 */
public interface RenderRequest {

    /**
     * Returns a copy of this request with the given scale factor applied,
     * if this request has not already set an explicit scale.
     *
     * <p>The default implementation returns {@code this} unchanged, which is
     * appropriate for request types that do not support scaling.
     *
     * @param scaleFactor the scale factor to inherit
     * @return a request with the inherited scale applied, or {@code this}
     */
    default RenderRequest withInheritedScale(int scaleFactor) {
        return this;
    }
}
