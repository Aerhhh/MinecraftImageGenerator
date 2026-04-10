package net.aerh.jigsaw.exception;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Checked exception thrown when NBT or text input cannot be parsed.
 *
 * <p>This signals a failure caused by external input rather than a programming error, so callers
 * are expected to handle or propagate it explicitly. Every instance may carry an optional map of
 * diagnostic key-value pairs accessible via {@link #getContext()}.
 *
 * @see UnsupportedFormatException
 * @see net.aerh.jigsaw.api.nbt.NbtParser
 */
public class ParseException extends Exception {

    private final Map<String, Object> context;

    /**
     * Constructs a {@code ParseException} with the given message and no diagnostic context.
     *
     * @param message a human-readable description of the parse failure
     */
    public ParseException(String message) {
        super(message);
        this.context = Collections.emptyMap();
    }

    /**
     * Constructs a {@code ParseException} with the given message and diagnostic context entries.
     *
     * @param message the human-readable description of the parse failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     */
    public ParseException(String message, Map<String, Object> context) {
        super(message);
        this.context = Collections.unmodifiableMap(new HashMap<>(context));
    }

    /**
     * Constructs a {@code ParseException} with the given message, diagnostic context, and cause.
     *
     * @param message the human-readable description of the parse failure
     * @param context a map of diagnostic key-value pairs; must not be {@code null}
     * @param cause   the underlying exception that triggered this parse failure
     */
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
