package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base for all runtime (programming-error) exceptions in the jigsaw module.
 *
 * <p>Subclasses signal misuse of the API, misconfigured registries, or broken effect pipelines -
 * conditions that indicate a bug in the calling code rather than recoverable input failures.
 * Recoverable input failures use the checked {@link ParseException} or {@link RenderException}
 * hierarchy instead.
 *
 * <p>Every instance carries an optional map of diagnostic key-value pairs accessible via
 * {@link #getContext()}, useful for structured logging or error reporting.
 *
 * @see RegistryException
 * @see ValidationException
 * @see EffectException
 */
public abstract class JigsawException extends RuntimeException {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code JigsawException} with the given message and no diagnostic context.
     *
     * @param message a human-readable description of the error
     */
    protected JigsawException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code JigsawException} with the given message and diagnostic context entries.
     *
     * @param message the human-readable description of the error
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    protected JigsawException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code JigsawException} with the given message, diagnostic context, and cause.
     *
     * @param message the human-readable description of the error
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this error
     */
    protected JigsawException(String message, Map<String, Object> context, Throwable cause) {
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
