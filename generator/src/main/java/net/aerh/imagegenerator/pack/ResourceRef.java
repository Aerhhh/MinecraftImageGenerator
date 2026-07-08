package net.aerh.imagegenerator.pack;

import java.util.regex.Pattern;

/**
 * A namespaced resource location ({@code namespace:path}) as used by the modern resource pack
 * format, plus mapping to the concrete asset file paths inside a pack.
 */
public record ResourceRef(String namespace, String path) {

    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]{1,64}");
    // Path segments: lowercase, digits, _ . -, separated by single slashes; no leading slash,
    // no '..'.
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    public ResourceRef {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("Invalid resource namespace: " + namespace);
        }
        if (path == null || !PATH.matcher(path).matches() || path.contains("..")) {
            throw new IllegalArgumentException("Invalid resource path: " + path);
        }
    }

    /**
     * Parses {@code "ns:path"}; a value without a namespace uses {@code defaultNamespace}, or is
     * rejected when {@code defaultNamespace} is null.
     */
    public static ResourceRef parse(String value, String defaultNamespace) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Resource reference must not be blank");
        }
        int colon = value.indexOf(':');
        if (colon < 0) {
            if (defaultNamespace == null) {
                throw new IllegalArgumentException("Resource reference must be namespaced: "
                        + value);
            }
            return new ResourceRef(defaultNamespace, value);
        }
        if (value.indexOf(':', colon + 1) >= 0) {
            throw new IllegalArgumentException("Resource reference has multiple colons: " + value);
        }
        return new ResourceRef(value.substring(0, colon), value.substring(colon + 1));
    }

    public String itemDefinitionPath() {
        return "assets/" + namespace + "/items/" + path + ".json";
    }

    public String modelPath() {
        return "assets/" + namespace + "/models/" + path + ".json";
    }

    public String texturePath() {
        return "assets/" + namespace + "/textures/" + path + ".png";
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
