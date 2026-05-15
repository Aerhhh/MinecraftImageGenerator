package net.aerh.imagegenerator.testsupport;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Test-only utility for reading classpath resources by relative path. Paths are relative to
 * the test classpath root (e.g. {@code "mojang/profile-success.json"} resolves to
 * {@code generator/src/test/resources/mojang/profile-success.json}).
 *
 * <p>Centralized so every later phase reuses one fixture-loading entry point rather than
 * inlining ClassLoader plumbing per test.</p>
 */
public final class TestResources {

    private TestResources() {
        // utility - instances are not meaningful
    }

    /**
     * Reads a UTF-8 classpath resource as a String.
     *
     * @param classpathPath relative path on the test classpath; must not be null
     *
     * @return file contents as a UTF-8 string
     *
     * @throws IllegalArgumentException if the resource is not found
     * @throws UncheckedIOException     if the resource cannot be read
     */
    public static String readString(String classpathPath) {
        try (InputStream in = openResource(classpathPath)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read test resource: " + classpathPath, ex);
        }
    }

    /**
     * Reads a classpath resource as raw bytes.
     *
     * @param classpathPath relative path on the test classpath; must not be null
     *
     * @return file contents as a byte array
     *
     * @throws IllegalArgumentException if the resource is not found
     * @throws UncheckedIOException     if the resource cannot be read
     */
    public static byte[] readBytes(String classpathPath) {
        try (InputStream in = openResource(classpathPath)) {
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read test resource: " + classpathPath, ex);
        }
    }

    private static InputStream openResource(String classpathPath) {
        InputStream in = Thread.currentThread()
            .getContextClassLoader()
            .getResourceAsStream(classpathPath);
        if (in == null) {
            throw new IllegalArgumentException("Missing test resource: " + classpathPath);
        }
        return in;
    }
}