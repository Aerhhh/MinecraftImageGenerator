package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base for all runtime (programming-error) exceptions in the jigsaw module.
 * <p>
 * Subclasses signal misuse of the API, misconfigured registries, or broken
 * effect pipelines - conditions that cannot be recovered from at the call site
 * and that indicate a bug in the calling code.
 */
public abstract class JigsawException extends RuntimeException {

    private final Map<String, Object> context;

    protected JigsawException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    protected JigsawException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

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
