package net.aerh.jigsaw.core.nbt;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.aerh.jigsaw.exception.ParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NbtTextComponentUtilTest {

    @Test
    void toFormattedString_jsonPrimitiveString_returnsStringUnchanged() {
        JsonElement element = JsonParser.parseString("\"hello\"");
        assertThat(NbtTextComponentUtil.toFormattedString(element)).isEqualTo("hello");
    }

    @Test
    void toFormattedString_componentWithColor_prependsColorCode() {
        JsonElement element = JsonParser.parseString("{\"color\":\"gold\",\"text\":\"loot\"}");
        assertThat(NbtTextComponentUtil.toFormattedString(element)).isEqualTo("&6loot");
    }

    @Test
    void toFormattedString_componentWithObfuscated_prependsKCode() {
        JsonElement element = JsonParser.parseString("{\"color\":\"light_purple\",\"text\":\"a\",\"bold\":true,\"obfuscated\":true}");
        assertThat(NbtTextComponentUtil.toFormattedString(element)).isEqualTo("&d&l&ka");
    }

    @Test
    void toFormattedString_snbtByteFlags_treatedAsBooleans() throws ParseException {
        String snbt = "{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b}";
        JsonElement parsed = SnbtParser.parse(snbt);
        assertThat(NbtTextComponentUtil.toFormattedString(parsed)).isEqualTo("&d&l&ka");
    }

    @Test
    void toFormattedString_parentItalicFalseForcesResetOnChildren() throws ParseException {
        // When a parent has italic:0b, children must emit &r so they don't inherit default italic
        // from Minecraft's "text" component semantics.
        String snbt = "{text:\"\",italic:0b,extra:[{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b}]}";
        JsonElement parsed = SnbtParser.parse(snbt);
        assertThat(NbtTextComponentUtil.toFormattedString(parsed)).isEqualTo("&r&d&l&ka");
    }

    @Test
    void toFormattedString_mythicBootsFooterLine_preservesObfuscatedOnBothEnds() throws ParseException {
        String snbt = "{extra:[{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b},\"\","
            + "{extra:[\" \"],underlined:0b,text:\"\",bold:0b,strikethrough:0b,italic:0b,obfuscated:0b},"
            + "{color:\"light_purple\",text:\"MYTHIC BOOTS \",bold:1b},"
            + "{color:\"light_purple\",text:\"a\",bold:1b,obfuscated:1b}],text:\"\",italic:0b}";
        JsonElement parsed = SnbtParser.parse(snbt);
        String formatted = NbtTextComponentUtil.toFormattedString(parsed);
        assertThat(formatted).startsWith("&r&d&l&ka");
        assertThat(formatted).endsWith("&d&l&ka");
        assertThat(formatted).contains("MYTHIC BOOTS");
    }

    @Test
    void extractVisibleText_skipsFormattingFields() {
        JsonElement element = JsonParser.parseString(
            "{\"text\":\"\",\"extra\":[{\"color\":\"red\",\"text\":\"hello \",\"bold\":true},{\"text\":\"world\"}]}");
        assertThat(NbtTextComponentUtil.extractVisibleText(element)).isEqualTo("hello world");
    }
}
