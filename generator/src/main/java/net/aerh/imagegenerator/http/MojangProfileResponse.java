package net.aerh.imagegenerator.http;

import org.jetbrains.annotations.Nullable;

/**
 * Response from {@code GET https://api.mojang.com/users/profiles/minecraft/{name}}. Mojang
 * returns {@code {"id":"<32-char-hex-uuid>","name":"<player-name>"}}.
 *
 * <p>Fields are nullable for defensive parity with the previous Marmalade-based traversal,
 * which performed null checks on missing keys. Phase 4 (INPUT-02) tightens this with the
 * Phase 2 validation contract.</p>
 *
 * @param id   profile UUID without dashes; nullable on malformed responses
 * @param name canonical profile name; nullable on malformed responses
 */
public record MojangProfileResponse(@Nullable String id,
                                    @Nullable String name) { }