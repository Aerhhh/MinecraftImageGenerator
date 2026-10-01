package net.aerh.imagegenerator.tools.pack;

/** The verdict on a candidate pack. */
public sealed interface PackValidation {

    record Valid() implements PackValidation {
    }

    /** @param reason A one-line, human-readable reason suitable for a log channel */
    record Invalid(String reason) implements PackValidation {
    }
}
