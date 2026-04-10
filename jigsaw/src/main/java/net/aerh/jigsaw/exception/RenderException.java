package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when a rendering operation fails.
 *
 * <p>Rendering failures are caused by external state such as missing resources or bad image data,
 * rather than programming errors. Callers must explicitly handle or propagate this exception.
 * Every instance may carry an optional map of diagnostic key-value pairs accessible via
 * {@link #getContext()}.
 *
 * @see RenderTimeoutException
 * @see net.aerh.jigsaw.api.Engine#renderItem(String)
 */
public class RenderException extends Exception {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code RenderException} with the given message and no diagnostic context.
     *
     * @param message a human-readable description of the render failure
     */
    public RenderException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code RenderException} with the given message and diagnostic context entries.
     *
     * @param message the human-readable description of the render failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public RenderException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code RenderException} with the given message, diagnostic context, and cause.
     *
     * @param message the human-readable description of the render failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this render failure
     */
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
