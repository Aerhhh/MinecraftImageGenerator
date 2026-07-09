package net.aerh.imagegenerator.http;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Response from {@code GET https://sessionserver.mojang.com/session/minecraft/profile/{uuid}}.
 * The shape consumed by {@code MinecraftPlayerHeadGenerator.getPlayerHeadURL} is the chain
 * {@code properties[0].value} - a base64-encoded textures JSON.
 *
 * <p>Phase 1 keeps fields nullable to preserve the existing null-check behavior at the call
 * site (the current code throws {@code GeneratorException} when {@code properties} is null);
 * Phase 4 (INPUT-02) replaces that with typed validation exceptions via the Phase 2 contract.</p>
 *
 * @param id         profile UUID without dashes
 * @param name       canonical profile name
 * @param properties texture-property entries; nullable on malformed responses
 */
public record MojangSessionResponse(@Nullable String id,
                                    @Nullable String name,
                                    @Nullable List<Property> properties) {

    /**
     * Single texture property entry. Skin lookups consume {@code value} (base64-encoded JSON).
     *
     * @param name      property name (typically "textures")
     * @param value     base64-encoded JSON payload - decoded inline today; Phase 4 hardens
     * @param signature Mojang-signed signature; not consumed in Phase 1
     */
    public record Property(@Nullable String name,
                           @Nullable String value,
                           @Nullable String signature) { }
}