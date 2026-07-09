package net.aerh.imagegenerator.http;

import feign.Feign;
import feign.gson.GsonDecoder;
import feign.http2client.Http2Client;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the process-wide default {@link MojangApiClient} used when a caller does not supply
 * one via {@code MinecraftPlayerHeadGenerator.Builder.withMojangApiClient(...)}.
 *
 * <p>Mojang serves the two endpoints from different hosts ({@code api.mojang.com} for UUID
 * lookup, {@code sessionserver.mojang.com} for the textures fetch). Feign's {@code target(...)}
 * binds an absolute base URL per instance, so the factory constructs TWO Feign-generated
 * targets and returns a composite delegator that routes each method to the correct host.
 * The single {@link MojangApiClient} test surface (mockable with vanilla Mockito) is preserved.</p>
 *
 * <p>Phase 3 (NET-01/NET-02) extends this factory with {@code Request.Options} (timeouts) and
 * Feign interceptors / {@code ErrorDecoder} (host allowlist). Phase 1 leaves a clean shape for
 * that extension - no preemptive options or interceptors.</p>
 */
public final class MojangApiClientFactory {

    private static final String PROFILE_API_BASE_URL = "https://api.mojang.com";
    private static final String SESSION_API_BASE_URL = "https://sessionserver.mojang.com";

    private MojangApiClientFactory() {
        // utility - instances are not meaningful
    }

    /**
     * Builds a fresh composite delegator backed by two Feign targets. Callers that need a
     * custom client (tests, integrators) pass their own instance to
     * {@code MinecraftPlayerHeadGenerator.Builder.withMojangApiClient(...)} and never invoke
     * this method.
     *
     * @return a {@link MojangApiClient} composing two Feign targets, one per Mojang host
     */
    public static @NotNull MojangApiClient defaultInstance() {
        MojangApiClient profileTarget = Feign.builder()
            .client(new Http2Client())
            .decoder(new GsonDecoder())
            .target(MojangApiClient.class, PROFILE_API_BASE_URL);

        MojangApiClient sessionTarget = Feign.builder()
            .client(new Http2Client())
            .decoder(new GsonDecoder())
            .target(MojangApiClient.class, SESSION_API_BASE_URL);

        return new MojangApiClient() {
            @Override
            public MojangProfileResponse lookupUuid(String name) {
                return profileTarget.lookupUuid(name);
            }

            @Override
            public MojangSessionResponse fetchProfile(String uuid) {
                return sessionTarget.fetchProfile(uuid);
            }
        };
    }
}