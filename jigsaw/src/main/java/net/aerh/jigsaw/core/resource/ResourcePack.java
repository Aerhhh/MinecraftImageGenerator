package net.aerh.jigsaw.core.resource;

import java.io.Closeable;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * Abstraction over a Minecraft resource pack source (folder or zip file).
 *
 * <p>Resource paths use forward slashes and are relative to the pack root,
 * e.g. {@code "assets/minecraft/textures/item/diamond_sword.png"}.
 */
public interface ResourcePack extends Closeable {

    /**
     * Opens an input stream for the resource at the given path.
     * The caller is responsible for closing the returned stream.
     *
     * @param path the resource path relative to the pack root
     * @return the input stream, or empty if the resource does not exist
     */
    Optional<InputStream> getResource(String path);

    /**
     * Returns whether a resource exists at the given path.
     *
     * @param path the resource path relative to the pack root
     * @return true if the resource exists
     */
    boolean hasResource(String path);

    /**
     * Lists all resource paths under the given prefix.
     *
     * @param prefix the path prefix to search under (e.g. {@code "assets/minecraft/models/item/"})
     * @return the set of matching resource paths
     */
    Set<String> listResources(String prefix);

    /**
     * Returns the parsed pack metadata.
     *
     * @return the pack metadata
     */
    PackMetadata metadata();
}
