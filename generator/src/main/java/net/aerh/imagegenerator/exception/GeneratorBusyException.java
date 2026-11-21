package net.aerh.imagegenerator.exception;

/** Thrown when a render is refused because the renders already running are using the memory it needs. */
public class GeneratorBusyException extends GeneratorException {

    public GeneratorBusyException(String message) {
        super(message);
    }
}
