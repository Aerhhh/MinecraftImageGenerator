package net.aerh.imagegenerator.pack;

import java.util.regex.Pattern;

/**
 * Library-level pack identity, e.g. {@code hypixel:skyblock}. Distinct from the asset namespace a
 * pack declares internally (e.g. {@code hypixel_skyblock}).
 */
public record PackId(String namespace, String name) {

    private static final Pattern PART = Pattern.compile("[a-z0-9_.-]{1,64}");
    public static final PackId VANILLA = new PackId("minecraft", "minecraft");

    public PackId {
        if (namespace == null || !PART.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid pack namespace: " + namespace);
        }
        if (name == null || !PART.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid pack name: " + name);
        }
    }

    /**
     * Parses a {@code "namespace:name"} string into a {@link PackId}.
     *
     * @throws IllegalArgumentException if the input is null, has no colon, more than one colon,
     *                                  or either part fails validation
     */
    public static PackId parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Pack ID must not be null");
        }
        int colon = value.indexOf(':');
        if (colon < 0 || value.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Pack ID must be 'namespace:name', got: " + value);
        }
        return new PackId(value.substring(0, colon), value.substring(colon + 1));
    }

    @Override
    public String toString() {
        return namespace + ":" + name;
    }
}
