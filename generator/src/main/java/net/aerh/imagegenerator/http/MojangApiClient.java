package net.aerh.imagegenerator.http;

import feign.Param;
import feign.RequestLine;
import org.jetbrains.annotations.NotNull;

/**
 * Typed HTTP client for the Mojang public profile and session APIs. Production wiring uses a
 * {@link MojangApiClientFactory#defaultInstance()} composite delegator over two Feign targets
 * (one per Mojang host). Tests mock this interface directly with Mockito - no static mocking
 * required.
 *
 * <p>Phase 1 introduces this seam; Phase 3 (NET-01/NET-02) attaches host allowlist enforcement
 * via Feign interceptors and connect/read timeouts via {@code Request.Options}.</p>
 */
public interface MojangApiClient {

    /**
     * Resolves a Minecraft player name to its profile (UUID + canonical name).
     * Maps to {@code GET https://api.mojang.com/users/profiles/minecraft/{name}}.
     *
     * @param name URL path segment - sanitized by the caller before reaching this method
     *
     * @return parsed profile response; {@link MojangProfileResponse#id()} may be null on partial Mojang responses
     *
     * @throws feign.FeignException on transport errors and non-2xx HTTP responses
     */
    @RequestLine("GET /users/profiles/minecraft/{name}")
    MojangProfileResponse lookupUuid(@Param("name") @NotNull String name);

    /**
     * Fetches the full profile (including the textures property) for a UUID.
     * Maps to {@code GET https://sessionserver.mojang.com/session/minecraft/profile/{uuid}}.
     *
     * @param uuid UUID without dashes
     *
     * @return parsed session response; {@link MojangSessionResponse#properties()} may be null on partial responses
     *
     * @throws feign.FeignException on transport errors and non-2xx HTTP responses
     */
    @RequestLine("GET /session/minecraft/profile/{uuid}")
    MojangSessionResponse fetchProfile(@Param("uuid") @NotNull String uuid);
}