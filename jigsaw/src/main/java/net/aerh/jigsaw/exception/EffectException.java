package net.aerh.jigsaw.exception;

import java.util.Map;

/**
 * Thrown when an effect in the rendering pipeline fails.
 * <p>
 * The ID of the failing effect is stored in the context under the key {@code "effectId"}.
 * Always wraps the underlying cause of the failure.
 */
public class EffectException extends JigsawException {

    public EffectException(String message, String effectId, Throwable cause) {
        super(message, Map.of("effectId", effectId), cause);
    }
}
