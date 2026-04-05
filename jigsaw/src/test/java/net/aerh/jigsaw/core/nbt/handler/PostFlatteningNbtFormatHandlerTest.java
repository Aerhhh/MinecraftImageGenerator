package net.aerh.jigsaw.core.nbt.handler;

import net.aerh.jigsaw.api.nbt.ParsedItem;
import net.aerh.jigsaw.exception.ParseException;
import net.aerh.jigsaw.spi.NbtFormatHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PostFlatteningNbtFormatHandlerTest {

    private static final NbtFormatHandlerContext CONTEXT = new NbtFormatHandlerContext() {
        @Override
        public <T> Optional<net.aerh.jigsaw.api.data.DataRegistry<T>> registry(
                net.aerh.jigsaw.api.data.RegistryKey<T> key) {
            return Optional.empty();
        }
    };

    private PostFlatteningNbtFormatHandler handler;

    @BeforeEach
    void setUp() {
        handler = new PostFlatteningNbtFormatHandler();
    }

    // --- id and priority ---

    @Test
    void id_returnsExpectedId() {
        assertThat(handler.id()).isEqualTo("jigsaw:post-flattening");
    }

    @Test
    void priority_is200() {
        assertThat(handler.priority()).isEqualTo(200);
    }

    // --- canHandle ---

    @Test
    void canHandle_jsonWithTagKey_returnsTrue() {
        assertThat(handler.canHandle("{\"id\":\"minecraft:sword\",\"tag\":{}}")).isTrue();
    }

    @Test
    void canHandle_snbtWithTagKey_returnsTrue() {
        assertThat(handler.canHandle("{id:\"minecraft:sword\",tag:{}}")).isTrue();
    }

    @Test
    void canHandle_withoutTagKey_returnsFalse() {
        assertThat(handler.canHandle("{\"id\":\"minecraft:stone\"}")).isFalse();
    }

    @Test
    void canHandle_nullInput_returnsFalse() {
        assertThat(handler.canHandle(null)).isFalse();
    }

    // --- parse: item ID ---

    @Test
    void parse_extractsItemId() throws ParseException {
        String input = "{\"id\":\"minecraft:diamond_sword\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.itemId()).isEqualTo("minecraft:diamond_sword");
    }

    @Test
    void parse_missingId_defaultsToAir() throws ParseException {
        String input = "{\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.itemId()).isEqualTo("minecraft:air");
    }

    // --- parse: enchanted ---

    @Test
    void parse_enchantmentsArray_nonEmpty_enchantedIsTrue() throws ParseException {
        String input = "{\"id\":\"minecraft:sword\",\"tag\":{"
                + "\"Enchantments\":[{\"id\":\"minecraft:sharpness\",\"lvl\":5}]}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isTrue();
    }

    @Test
    void parse_enchantmentsArray_empty_enchantedIsFalse() throws ParseException {
        String input = "{\"id\":\"minecraft:sword\",\"tag\":{\"Enchantments\":[]}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isFalse();
    }

    @Test
    void parse_storedEnchantments_nonEmpty_enchantedIsTrue() throws ParseException {
        String input = "{\"id\":\"minecraft:book\",\"tag\":{"
                + "\"StoredEnchantments\":[{\"id\":\"minecraft:fortune\",\"lvl\":3}]}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isTrue();
    }

    @Test
    void parse_noEnchantments_enchantedIsFalse() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.enchanted()).isFalse();
    }

    // --- parse: display name ---

    @Test
    void parse_displayNameAsJsonText_returnsFormattedName() throws ParseException {
        String input = "{\"id\":\"minecraft:sword\",\"tag\":{"
                + "\"display\":{\"Name\":\"{\\\"text\\\":\\\"My Sword\\\"}\"}"
                + "}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.displayName()).isPresent();
        assertThat(item.displayName().get()).isEqualTo("My Sword");
    }

    @Test
    void parse_noDisplay_returnsEmptyDisplayName() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.displayName()).isEmpty();
    }

    // --- parse: lore ---

    @Test
    void parse_loreLines_returnsAllLines() throws ParseException {
        String input = "{\"id\":\"minecraft:sword\",\"tag\":{"
                + "\"display\":{\"Lore\":["
                + "\"{\\\"text\\\":\\\"First line\\\"}\","
                + "\"{\\\"text\\\":\\\"Second line\\\"}\""
                + "]}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.lore()).hasSize(2);
    }

    @Test
    void parse_noLore_returnsEmptyList() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.lore()).isEmpty();
    }

    // --- parse: dye color ---

    @Test
    void parse_dyeColorInDisplay_returnsColor() throws ParseException {
        String input = "{\"id\":\"minecraft:leather_chestplate\",\"tag\":{"
                + "\"display\":{\"color\":16711680}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.dyeColor()).isPresent();
        assertThat(item.dyeColor().get()).isEqualTo(16711680);
    }

    @Test
    void parse_noDyeColor_returnsEmpty() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.dyeColor()).isEmpty();
    }

    // --- parse: skull texture ---

    @Test
    void parse_skullOwnerTexture_extractsBase64Value() throws ParseException {
        String input = "{\"id\":\"minecraft:skull\",\"tag\":{"
                + "\"SkullOwner\":{"
                + "\"Properties\":{"
                + "\"textures\":[{\"Value\":\"eyJ0ZXh0dXJlcyI6e319\"}]"
                + "}}}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.base64Texture()).isPresent();
        assertThat(item.base64Texture().get()).isEqualTo("eyJ0ZXh0dXJlcyI6e319");
    }

    @Test
    void parse_noSkullOwner_returnsEmptyTexture() throws ParseException {
        String input = "{\"id\":\"minecraft:stone\",\"tag\":{}}";
        ParsedItem item = handler.parse(input, CONTEXT);

        assertThat(item.base64Texture()).isEmpty();
    }
}
