package net.aerh.jigsaw.core.nbt.handler;

import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentsNbtFormatHandlerTest {

    private static final NbtFormatHandlerContext CONTEXT = new NbtFormatHandlerContext() {
        @Override
        public <T> Optional<net.aerh.jigsaw.api.data.DataRegistry<T>> registry(
                net.aerh.jigsaw.api.data.RegistryKey<T> key) {
            return Optional.empty();
        }
    };

    private ComponentsNbtFormatHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ComponentsNbtFormatHandler();
    }

    // --- id and priority ---

    @Test
    void id_returnsExpectedId() {
        assertThat(handler.id()).isEqualTo("jigsaw:components");
    }

    @Test
    void priority_is100() {
        assertThat(handler.priority()).isEqualTo(100);
    }

    // --- canHandle ---

    @Test
    void canHandle_jsonWithComponentsKey_returnsTrue() {
        assertThat(handler.canHandle("{\"id\":\"minecraft:diamond\",\"components\":{}}")).isTrue();
    }

    @Test
    void canHandle_snbtWithComponentsKey_returnsTrue() {
        assertThat(handler.canHandle("{id:\"minecraft:diamond\",components:{}}")).isTrue();
    }

    @Test
    void canHandle_withoutComponentsKey_returnsFalse() {
        assertThat(handler.canHandle("{\"id\":\"minecraft:stone\",\"tag\":{}}")).isFalse();
    }

    @Test
    void canHandle_nullInput_returnsFalse() {
        assertThat(handler.canHandle(null)).isFalse();
    }

    @Test
    void canHandle_blankInput_returnsFalse() {
        assertThat(handler.canHandle("   ")).isFalse();
    }

    // --- parse: item ID ---

    @Test
    void parse_extractsItemId() throws ParseException {
        String input = "{\"id\":\"minecraft:diamond_sword\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.itemId()).isEqualTo("diamond_sword");
    }

    @Test
    void parse_missingId_defaultsToAir() throws ParseException {
        String input = "{\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.itemId()).isEqualTo("air");
    }

    // --- parse: enchanted ---

    @Test
    void parse_enchantmentGlintOverrideTrue_enchantedIsTrue() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{\"minecraft:enchantment_glint_override\":true}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isTrue();
    }

    @Test
    void parse_enchantmentGlintOverrideFalse_enchantedIsFalse() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{\"minecraft:enchantment_glint_override\":false}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isFalse();
    }

    @Test
    void parse_enchantmentsWithEntries_enchantedIsTrue() throws ParseException {
        String input = "{\"id\":\"minecraft:sword\",\"components\":{\"minecraft:enchantments\":{\"sharpness\":5}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isTrue();
    }

    @Test
    void parse_noEnchantments_enchantedIsFalse() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isFalse();
    }

    // --- parse: lore ---

    @Test
    void parse_loreAsJsonTextComponents_returnsFormattedStrings() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{\"minecraft:lore\":["
                + "\"{\\\"text\\\":\\\"Line 1\\\",\\\"color\\\":\\\"gold\\\"}\","
                + "\"{\\\"text\\\":\\\"Line 2\\\"}\"]}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.lore()).hasSize(2);
    }

    @Test
    void parse_noLore_returnsEmptyList() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.lore()).isEmpty();
    }

    // --- parse: display name ---

    @Test
    void parse_customNameAsJsonTextComponent_returnsDisplayName() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{"
                + "\"minecraft:custom_name\":\"{\\\"text\\\":\\\"My Stone\\\"}\"}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.displayName()).isPresent();
        assertThat(item.displayName().get()).isEqualTo("My Stone");
    }

    @Test
    void parse_noCustomName_returnsEmptyDisplayName() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.displayName()).isEmpty();
    }

    // --- parse: dye color ---

    @Test
    void parse_dyedColorRgbObject_returnsColor() throws ParseException {
        String input = "{\"id\":\"minecraft:leather_chestplate\",\"components\":{"
                + "\"minecraft:dyed_color\":{\"rgb\":16711680}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.dyeColor()).isPresent();
        assertThat(item.dyeColor().get()).isEqualTo(16711680);
    }

    @Test
    void parse_noDyeColor_returnsEmpty() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.dyeColor()).isEmpty();
    }

    // --- parse: skull texture ---

    @Test
    void parse_skullProfile_extractsBase64Texture() throws ParseException {
        String input = "{\"id\":\"minecraft:player_head\",\"components\":{"
                + "\"minecraft:profile\":{\"properties\":[{"
                + "\"name\":\"textures\","
                + "\"value\":\"eyJ0ZXh0dXJlcyI6e319\"}]}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.base64Texture()).isPresent();
        assertThat(item.base64Texture().get()).isEqualTo("eyJ0ZXh0dXJlcyI6e319");
    }

    @Test
    void parse_noProfile_returnsEmptyTexture() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"components\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.base64Texture()).isEmpty();
    }
}
