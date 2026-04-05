package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when a rendering operation fails.
 * <p>
 * Rendering failures are caused by external state (missing resources, bad image
 * data, etc.) rather than programming errors, so callers must handle or propagate
 * this explicitly.
 */
public class RenderException extends Exception {

    private final Map<String, Object> context;

    public RenderException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    public RenderException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    public RenderException(String message, Map<String, Object> context, Throwable cause) {
        super(message, cause);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Returns an unmodifiable map of diagnostic key-value pairs attached to this exception.
     */
    public Map<String, Object> getContext() {
        return context;
    }
}
