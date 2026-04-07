package net.aerh.jigsaw.core.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.aerh.jigsaw.exception.ParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnbtParserTest {

    // --- empty compound ---

    @Test
    void parse_emptyCompound_returnsEmptyObject() throws ParseException {
        JsonElement result = SnbtParser.parse("{}");

        assertThat(result.isJsonObject()).isTrue();
        assertThat(result.getAsJsonObject().size()).isZero();
    }

    // --- string values ---

    @Test
    void parse_quotedStringValue_returnsString() throws ParseException {
        JsonElement result = SnbtParser.parse("{id:\"minecraft:diamond_sword\"}");

        assertThat(result.getAsJsonObject().get("id").getAsString())
                .isEqualTo("minecraft:diamond_sword");
    }

    @Test
    void parse_singleQuotedStringValue_returnsString() throws ParseException {
        JsonElement result = SnbtParser.parse("{id:'hello world'}");

        assertThat(result.getAsJsonObject().get("id").getAsString()).isEqualTo("hello world");
    }

    @Test
    void parse_unquotedStringValue_returnsString() throws ParseException {
        JsonElement result = SnbtParser.parse("{id:someValue}");

        assertThat(result.getAsJsonObject().get("id").getAsString()).isEqualTo("someValue");
    }

    // --- numeric types ---

    @Test
    void parse_integerValue_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{count:42}");

        assertThat(result.getAsJsonObject().get("count").getAsInt()).isEqualTo(42);
    }

    @Test
    void parse_byteValue_suffixB_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{flag:1b}");

        assertThat(result.getAsJsonObject().get("flag").getAsInt()).isEqualTo(1);
    }

    @Test
    void parse_shortValue_suffixS_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{val:100s}");

        assertThat(result.getAsJsonObject().get("val").getAsInt()).isEqualTo(100);
    }

    @Test
    void parse_longValue_suffixL_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{val:1234567890L}");

        assertThat(result.getAsJsonObject().get("val").getAsLong()).isEqualTo(1234567890L);
    }

    @Test
    void parse_floatValue_suffixF_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{val:1.5f}");

        assertThat(result.getAsJsonObject().get("val").getAsFloat()).isEqualTo(1.5f);
    }

    @Test
    void parse_doubleValue_suffixD_returnsNumber() throws ParseException {
        JsonElement result = SnbtParser.parse("{val:3.14d}");

        assertThat(result.getAsJsonObject().get("val").getAsDouble()).isCloseTo(3.14, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    void parse_floatValue_noSuffix_returnsDouble() throws ParseException {
        JsonElement result = SnbtParser.parse("{val:2.718}");

        assertThat(result.getAsJsonObject().get("val").getAsDouble()).isCloseTo(2.718, org.assertj.core.data.Offset.offset(0.001));
    }

    // --- booleans ---

    @Test
    void parse_booleanTrue_returnsByte1() throws ParseException {
        JsonElement result = SnbtParser.parse("{flag:true}");

        assertThat(result.getAsJsonObject().get("flag").getAsInt()).isEqualTo(1);
    }

    @Test
    void parse_booleanFalse_returnsByte0() throws ParseException {
        JsonElement result = SnbtParser.parse("{flag:false}");

        assertThat(result.getAsJsonObject().get("flag").getAsInt()).isEqualTo(0);
    }

    // --- lists ---

    @Test
    void parse_emptyList_returnsEmptyArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{items:[]}");

        assertThat(result.getAsJsonObject().get("items").isJsonArray()).isTrue();
        assertThat(result.getAsJsonObject().get("items").getAsJsonArray().size()).isZero();
    }

    @Test
    void parse_listWithValues_returnsArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{items:[1,2,3]}");

        JsonArray arr = result.getAsJsonObject().get("items").getAsJsonArray();
        assertThat(arr.size()).isEqualTo(3);
        assertThat(arr.get(0).getAsInt()).isEqualTo(1);
        assertThat(arr.get(2).getAsInt()).isEqualTo(3);
    }

    // --- typed arrays ---

    @Test
    void parse_byteArray_returnsJsonArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{data:[B;1b,2b,3b]}");

        JsonArray arr = result.getAsJsonObject().get("data").getAsJsonArray();
        assertThat(arr.size()).isEqualTo(3);
    }

    @Test
    void parse_intArray_returnsJsonArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{data:[I;10,20,30]}");

        JsonArray arr = result.getAsJsonObject().get("data").getAsJsonArray();
        assertThat(arr.size()).isEqualTo(3);
        assertThat(arr.get(1).getAsInt()).isEqualTo(20);
    }

    @Test
    void parse_longArray_returnsJsonArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{data:[L;100L,200L]}");

        JsonArray arr = result.getAsJsonObject().get("data").getAsJsonArray();
        assertThat(arr.size()).isEqualTo(2);
    }

    // --- nested compound ---

    @Test
    void parse_nestedCompound_returnsNestedObject() throws ParseException {
        JsonElement result = SnbtParser.parse("{outer:{inner:\"value\"}}");

        JsonObject outer = result.getAsJsonObject();
        assertThat(outer.has("outer")).isTrue();
        JsonObject inner = outer.getAsJsonObject("outer");
        assertThat(inner.get("inner").getAsString()).isEqualTo("value");
    }

    // --- multiple keys ---

    @Test
    void parse_multipleKeys_allPresent() throws ParseException {
        JsonElement result = SnbtParser.parse("{id:\"minecraft:stone\",Count:1b,Damage:0s}");

        JsonObject obj = result.getAsJsonObject();
        assertThat(obj.get("id").getAsString()).isEqualTo("minecraft:stone");
        assertThat(obj.get("Count").getAsInt()).isEqualTo(1);
        assertThat(obj.get("Damage").getAsInt()).isEqualTo(0);
    }

    // --- indexed list format ---

    @Test
    void parse_indexedListFormat_returnsArray() throws ParseException {
        JsonElement result = SnbtParser.parse("{items:[0:\"a\",1:\"b\"]}");

        JsonArray arr = result.getAsJsonObject().get("items").getAsJsonArray();
        assertThat(arr.size()).isEqualTo(2);
        assertThat(arr.get(0).getAsString()).isEqualTo("a");
        assertThat(arr.get(1).getAsString()).isEqualTo("b");
    }

    // --- whitespace tolerance ---

    @Test
    void parse_whitespaceAroundTokens_parsesCorrectly() throws ParseException {
        JsonElement result = SnbtParser.parse("{ id : \"test\" , Count : 5 }");

        JsonObject obj = result.getAsJsonObject();
        assertThat(obj.get("id").getAsString()).isEqualTo("test");
        assertThat(obj.get("Count").getAsInt()).isEqualTo(5);
    }

    // --- non-breaking spaces (common when copying from Discord/web) ---

    @Test
    void parse_nonBreakingSpaces_treatedAsWhitespace() throws ParseException {
        // U+00A0 non-breaking spaces used as indentation (common from web/Discord copy-paste)
        String snbt = "{\u00A0\u00A0id:\u00A0\"test\",\u00A0Count:\u00A01b\u00A0}";
        JsonElement result = SnbtParser.parse(snbt);

        JsonObject obj = result.getAsJsonObject();
        assertThat(obj.get("id").getAsString()).isEqualTo("test");
        assertThat(obj.get("Count").getAsInt()).isEqualTo(1);
    }

    @Test
    void parse_mixedWhitespace_parsesCorrectly() throws ParseException {
        // Mix of regular spaces, tabs, and non-breaking spaces
        String snbt = "{\t\u00A0id: \"value\" ,\u00A0 nested:\u00A0{\u00A0key: 42\u00A0}\u00A0}";
        JsonElement result = SnbtParser.parse(snbt);

        JsonObject obj = result.getAsJsonObject();
        assertThat(obj.get("id").getAsString()).isEqualTo("value");
        assertThat(obj.getAsJsonObject("nested").get("key").getAsInt()).isEqualTo(42);
    }

    // --- error cases ---

    @Test
    void parse_null_throwsParseException() {
        assertThatThrownBy(() -> SnbtParser.parse(null))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void parse_emptyString_throwsParseException() {
        assertThatThrownBy(() -> SnbtParser.parse(""))
                .isInstanceOf(ParseException.class);
    }

    @Test
    void parse_unclosedBrace_throwsParseException() {
        assertThatThrownBy(() -> SnbtParser.parse("{id:\"test\""))
                .isInstanceOf(ParseException.class);
    }
}
