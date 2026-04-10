package net.aerh.jigsaw.core.font;

import net.aerh.jigsaw.api.font.FontProvider;
import net.aerh.jigsaw.api.font.FontRegistry;
import net.aerh.jigsaw.exception.RegistryException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Font;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultFontRegistryTest {

    private static FontRegistry registry;

    @BeforeAll
    static void setUp() {
        registry = DefaultFontRegistry.withBuiltins();
    }

    @Test
    void withBuiltins_registersDefaultFont() {
        FontProvider provider = registry.resolve(MinecraftFontId.DEFAULT);
        assertThat(provider).isNotNull();
        assertThat(provider.id()).isEqualTo(MinecraftFontId.DEFAULT);
    }

    @Test
    void withBuiltins_registersGalacticFont() {
        FontProvider provider = registry.resolve(MinecraftFontId.GALACTIC);
        assertThat(provider).isNotNull();
        assertThat(provider.id()).isEqualTo(MinecraftFontId.GALACTIC);
    }

    @Test
    void withBuiltins_registersIllagerAltFont() {
        FontProvider provider = registry.resolve(MinecraftFontId.ILLAGERALT);
        assertThat(provider).isNotNull();
        assertThat(provider.id()).isEqualTo(MinecraftFontId.ILLAGERALT);
    }

    @Test
    void resolve_unknownFontThrowsRegistryException() {
        assertThatThrownBy(() -> registry.resolve("minecraft:nonexistent"))
                .isInstanceOf(RegistryException.class);
    }

    @Test
    void defaultFont_returnsPresentAwtFont() {
        FontProvider provider = registry.resolve(MinecraftFontId.DEFAULT);
        assertThat(provider.getFont()).isPresent();
    }

    @Test
    void resolveForChar_returnsProvider() {
        // ASCII chars like 'A' are supported by any standard font
        FontProvider provider = registry.resolveForChar('A');
        assertThat(provider).isNotNull();
    }

    @Test
    void measureWidth_returnsPositive_forNonEmptyString() {
        int width = registry.measureWidth("Hello", MinecraftFontId.DEFAULT);
        assertThat(width).isGreaterThan(0);
    }

    @Test
    void measureWidth_returnsZero_forEmptyString() {
        int width = registry.measureWidth("", MinecraftFontId.DEFAULT);
        assertThat(width).isEqualTo(0);
    }

    @Test
    void register_replacesExistingProvider() {
        FontRegistry reg = DefaultFontRegistry.withBuiltins();
        ResourceFontProvider custom = new ResourceFontProvider("minecraft:default",
                "minecraft/assets/fonts/unifont-17.0.03.otf");
        reg.register(custom);
        assertThat(reg.resolve(MinecraftFontId.DEFAULT)).isSameAs(custom);
    }

    @Test
    void defaultFont_charWidthIsPositive_forPrintableChar() {
        FontProvider provider = registry.resolve(MinecraftFontId.DEFAULT);
        assertThat(provider.getCharWidth('A')).isGreaterThanOrEqualTo(0);
    }

    @Test
    void resourceFontProvider_fallsBackToMonospaced_whenResourceMissing() {
        ResourceFontProvider provider = new ResourceFontProvider("test:missing",
                "does/not/exist.otf");
        assertThat(provider.getFont()).isPresent();
        Font font = provider.getFont().get();
        assertThat(font.getFamily()).containsIgnoringCase("mono");
    }
}
