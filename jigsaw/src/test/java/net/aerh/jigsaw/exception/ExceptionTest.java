package net.aerh.jigsaw.exception;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    // Test 1: JigsawException subclasses carry context map and are RuntimeException
    @Test
    void baseExceptionSubclassIsRuntimeExceptionAndCarriesContext() {
        Map<String, Object> context = Map.of("key", "value", "count", 42);
        ValidationException ex = new ValidationException("bad usage", context);

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex).isInstanceOf(JigsawException.class);
        assertThat(ex.getContext()).containsEntry("key", "value");
        assertThat(ex.getContext()).containsEntry("count", 42);
        assertThat(ex.getMessage()).isEqualTo("bad usage");
    }

    // Test 2: JigsawException with empty context returns empty map
    @Test
    void baseExceptionWithMessageOnlyHasEmptyContext() {
        ValidationException ex = new ValidationException("bad usage");

        assertThat(ex.getContext()).isEmpty();
    }

    // Test 3: ParseException is checked (extends Exception, NOT RuntimeException)
    @Test
    void parseExceptionIsChecked() {
        ParseException ex = new ParseException("parse failed");

        assertThat(ex).isInstanceOf(Exception.class);
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("parse failed");
        assertThat(ex.getContext()).isEmpty();
    }

    // Test 3b: ParseException carries context
    @Test
    void parseExceptionCarriesContext() {
        Map<String, Object> context = Map.of("input", "bad-nbt");
        ParseException ex = new ParseException("parse failed", context);

        assertThat(ex.getContext()).containsEntry("input", "bad-nbt");
    }

    // Test 4: RenderException is checked
    @Test
    void renderExceptionIsChecked() {
        RenderException ex = new RenderException("render failed");

        assertThat(ex).isInstanceOf(Exception.class);
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("render failed");
        assertThat(ex.getContext()).isEmpty();
    }

    // Test 4b: RenderException carries context
    @Test
    void renderExceptionCarriesContext() {
        Map<String, Object> context = Map.of("layer", "overlay");
        RenderException ex = new RenderException("render failed", context);

        assertThat(ex.getContext()).containsEntry("layer", "overlay");
    }

    // Test 5: RenderTimeoutException extends RenderException, stores timeoutMs
    @Test
    void renderTimeoutExceptionExtendsRenderExceptionAndStoresTimeoutMs() {
        RenderTimeoutException ex = new RenderTimeoutException(5000L);

        assertThat(ex).isInstanceOf(RenderException.class);
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
        assertThat(ex.getTimeoutMs()).isEqualTo(5000L);
    }

    // Test 6: UnsupportedFormatException extends ParseException
    @Test
    void unsupportedFormatExceptionExtendsParseException() {
        UnsupportedFormatException ex = new UnsupportedFormatException("no handler found");

        assertThat(ex).isInstanceOf(ParseException.class);
        assertThat(ex).isNotInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("no handler found");
    }

    // Test 6b: UnsupportedFormatException carries context
    @Test
    void unsupportedFormatExceptionCarriesContext() {
        Map<String, Object> context = Map.of("format", "unknown");
        UnsupportedFormatException ex = new UnsupportedFormatException("no handler found", context);

        assertThat(ex.getContext()).containsEntry("format", "unknown");
    }

    // Test 7: RegistryException is runtime
    @Test
    void registryExceptionIsRuntime() {
        RegistryException ex = new RegistryException("registry broken");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex).isInstanceOf(JigsawException.class);
        assertThat(ex.getMessage()).isEqualTo("registry broken");
        assertThat(ex.getContext()).isEmpty();
    }

    // Test 8: UnknownItemException extends RegistryException, stores itemId in context
    @Test
    void unknownItemExceptionExtendsRegistryExceptionAndStoresItemId() {
        UnknownItemException ex = new UnknownItemException("ASPECT_OF_THE_END");

        assertThat(ex).isInstanceOf(RegistryException.class);
        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getContext()).containsEntry("itemId", "ASPECT_OF_THE_END");
    }

    // Test 9: EffectException is runtime, stores effectId in context, wraps cause
    @Test
    void effectExceptionIsRuntimeAndStoresEffectIdAndCause() {
        Throwable cause = new IllegalStateException("underlying failure");
        EffectException ex = new EffectException("effect failed", "glint", cause);

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex).isInstanceOf(JigsawException.class);
        assertThat(ex.getContext()).containsEntry("effectId", "glint");
        assertThat(ex.getCause()).isSameAs(cause);
        assertThat(ex.getMessage()).isEqualTo("effect failed");
    }

    // Edge case: context map returned by getContext() is unmodifiable
    @Test
    void contextMapIsUnmodifiable() {
        Map<String, Object> context = Map.of("key", "value");
        ValidationException ex = new ValidationException("msg", context);

        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> ex.getContext().put("new", "entry")
        );
    }

    // Edge case: ParseException context map is unmodifiable
    @Test
    void parseExceptionContextMapIsUnmodifiable() {
        Map<String, Object> context = Map.of("key", "value");
        ParseException ex = new ParseException("msg", context);

        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> ex.getContext().put("new", "entry")
        );
    }

    // Edge case: RenderException context map is unmodifiable
    @Test
    void renderExceptionContextMapIsUnmodifiable() {
        Map<String, Object> context = Map.of("key", "value");
        RenderException ex = new RenderException("msg", context);

        org.junit.jupiter.api.Assertions.assertThrows(
            UnsupportedOperationException.class,
            () -> ex.getContext().put("new", "entry")
        );
    }

    // Edge case: RegistryException carries context
    @Test
    void registryExceptionCarriesContext() {
        Map<String, Object> context = Map.of("registry", "items");
        RegistryException ex = new RegistryException("broken", context);

        assertThat(ex.getContext()).containsEntry("registry", "items");
    }

    // Edge case: RenderTimeoutException context contains timeoutMs
    @Test
    void renderTimeoutExceptionContextContainsTimeoutMs() {
        RenderTimeoutException ex = new RenderTimeoutException(3000L);

        assertThat(ex.getContext()).containsEntry("timeoutMs", 3000L);
    }
}
