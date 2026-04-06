package net.aerh.jigsaw.core.resource;

/**
 * Parsed metadata from a resource pack's {@code pack.mcmeta} file.
 *
 * @param packFormat  the declared pack format version
 * @param description the human-readable pack description
 */
public record PackMetadata(int packFormat, String description) {

    public PackMetadata {
        if (packFormat < 0) {
            throw new IllegalArgumentException("packFormat must be >= 0, got: " + packFormat);
        }
        if (description == null) {
            description = "";
        }
    }
}
