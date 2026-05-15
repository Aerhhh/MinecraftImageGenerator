package net.aerh.imagegenerator.impl;

import feign.FeignException;
import feign.Request;
import net.aerh.imagegenerator.exception.GeneratorException;
import net.aerh.imagegenerator.http.MojangApiClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Rejection-path canary. Proves the Phase 1 test stack works end-to-end:
 * <ul>
 *   <li>Surefire discovers a JUnit 5 test under {@code generator/src/test/java}</li>
 *   <li>Mockito's {@code -javaagent} agent (via parent pom surefire pluginManagement) self-attaches without warnings</li>
 *   <li>{@link MockitoExtension} populates {@code @Mock} fields</li>
 *   <li>{@link MinecraftPlayerHeadGenerator.Builder#withMojangApiClient(MojangApiClient)} injects a Mockito-mocked client</li>
 *   <li>A simulated Mojang 404 raised by Feign surfaces as the expected {@link GeneratorException}</li>
 * </ul>
 * Pairs with TEST-02 - the rejection-path half of the paired-test pattern that activates from Phase 2 onward.
 */
@ExtendWith(MockitoExtension.class)
class MinecraftPlayerHeadGeneratorTest {

    @Mock
    MojangApiClient mojangClient;

    @Test
    void render_whenMojangReturns404_throwsGeneratorExceptionWithExpectedMessage() {
        when(mojangClient.lookupUuid(any())).thenThrow(buildFeignNotFound());

        MinecraftPlayerHeadGenerator generator = new MinecraftPlayerHeadGenerator.Builder()
            .withMojangApiClient(mojangClient)
            .withSkin("notarealplayer")
            .build();

        GeneratorException thrown = assertThrows(
            GeneratorException.class,
            () -> generator.render(null),
            "404 from Mojang should surface as GeneratorException"
        );

        assertNotNull(thrown.getMessage(), "GeneratorException should carry a formatted message");
        assertTrue(
            thrown.getMessage().contains("Could not find player with name"),
            () -> "expected message to contain 'Could not find player with name', got: " + thrown.getMessage()
        );
    }

    private FeignException.NotFound buildFeignNotFound() {
        Request stubRequest = Request.create(
            Request.HttpMethod.GET,
            "https://api.mojang.com/users/profiles/minecraft/notarealplayer",
            Map.of(),
            null,
            StandardCharsets.UTF_8,
            null
        );
        return new FeignException.NotFound("not found", stubRequest, null, Map.of());
    }
}