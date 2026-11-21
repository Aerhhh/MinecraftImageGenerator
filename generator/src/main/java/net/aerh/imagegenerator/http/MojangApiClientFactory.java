package net.aerh.imagegenerator.http;

import feign.Feign;
import feign.Request;
import feign.gson.GsonDecoder;
import feign.http2client.Http2Client;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.TimeUnit;

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
 * <p>Both targets use explicit connect and read timeouts and do not follow redirects.</p>
 */
public final class MojangApiClientFactory {

    private static final String PROFILE_API_BASE_URL = "https://api.mojang.com";
    private static final String SESSION_API_BASE_URL = "https://sessionserver.mojang.com";

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    /** Holder idiom: the shared client is built on first use and then reused for every lookup. */
    private static final class Shared {
        private static final MojangApiClient INSTANCE = defaultInstance();
    }

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
    /**
     * The process-wide client. Each {@link #defaultInstance()} call builds two HTTP clients with
     * their own threads, so the player head generator shares this one instead of building a pair
     * per render.
     *
     * @return the shared {@link MojangApiClient}
     */
    public static @NotNull MojangApiClient sharedInstance() {
        return Shared.INSTANCE;
    }

    public static @NotNull MojangApiClient defaultInstance() {
        Request.Options options = new Request.Options(
            CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS, READ_TIMEOUT_MS, TimeUnit.MILLISECONDS, false);

        MojangApiClient profileTarget = Feign.builder()
            .client(new Http2Client())
            .decoder(new GsonDecoder())
            .options(options)
            .target(MojangApiClient.class, PROFILE_API_BASE_URL);

        MojangApiClient sessionTarget = Feign.builder()
            .client(new Http2Client())
            .decoder(new GsonDecoder())
            .options(options)
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