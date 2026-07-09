package net.aerh.imagegenerator.exception;

/**
 * Thrown when a registered pack cannot resolve a requested item to a renderable sprite
 * (unsupported node types, missing models or textures, broken references).
 */
public class PackResolveException extends GeneratorException {

    public PackResolveException(String message) {
        super(message);
    }

    public PackResolveException(String message, String... formatArgs) {
        super(message, formatArgs);
    }

    public PackResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
