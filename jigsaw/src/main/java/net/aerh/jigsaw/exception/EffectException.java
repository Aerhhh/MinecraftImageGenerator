package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when an effect in the rendering pipeline fails.
 *
 * <p>The ID of the failing effect is stored in the diagnostic context under the key
 * {@code "effectId"}. This exception always wraps the underlying cause of the failure.
 *
 * @see net.aerh.jigsaw.api.effect.ImageEffect
 */
public class EffectException extends JigsawException {

    /**
     * Constructs an {@code EffectException} identifying the failing effect and its root cause.
     *
     * @param message  a human-readable description of the failure
     * @param effectId the {@link net.aerh.jigsaw.api.effect.ImageEffect#id()} of the failing effect
     * @param cause    the underlying exception thrown by the effect
     */
    public EffectException(String message, String effectId, Throwable cause) {
        super(message, Map.of("effectId", effectId), cause);
    }
}
