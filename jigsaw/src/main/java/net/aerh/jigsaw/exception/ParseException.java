package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when NBT or text input cannot be parsed.
 * <p>
 * This signals a failure caused by external input, not a programming error,
 * so callers are expected to handle or propagate it explicitly.
 */
public class ParseException extends Exception {

    private final Map<String, Object> context;

    public ParseException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    public ParseException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    public ParseException(String message, Map<String, Object> context, Throwable cause) {
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
