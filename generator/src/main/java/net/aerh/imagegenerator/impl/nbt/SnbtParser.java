package net.aerh.imagegenerator.impl.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.aerh.imagegenerator.exception.NbtParseException;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parses Stringified NBT (SNBT) into Gson {@link JsonElement} trees.
 * <p>
 * This parser handles both the modern Minecraft SNBT format and the
 * legacy indexed-list format ({@code [0: value, 1: value]}).
 * <p>
 * Supported tag types:
 * <ul>
 *   <li>Compound tags: {@code {key: value, "quoted key": value}}</li>
 *   <li>List tags: {@code [value, value]} and indexed {@code [0: value, 1: value]}</li>
 *   <li>Typed arrays: {@code [B; 1b, 2b]}, {@code [I; 1, 2]}, {@code [L; 1L, 2L]}</li>
 *   <li>String tags: {@code "quoted"} with backslash escapes</li>
 *   <li>Numeric tags: {@code 1b}, {@code 1s}, {@code 1}, {@code 1L}, {@code 1.0f}, {@code 1.0d}</li>
 *   <li>Boolean literals: {@code true}/{@code false} stored as JSON booleans</li>
 * </ul>
 */
public final class SnbtParser {

    private static final Pattern DOUBLE_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+[dD]");
    private static final Pattern FLOAT_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]+[fF]");
    private static final Pattern BYTE_PATTERN = Pattern.compile("[-+]?[0-9]+[bB]");
    private static final Pattern LONG_PATTERN = Pattern.compile("[-+]?[0-9]+[lL]");
    private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?[0-9]+[sS]");
    private static final Pattern INTEGER_PATTERN = Pattern.compile("[-+]?[0-9]+");
    private static final Pattern UNTYPED_DOUBLE_PATTERN = Pattern.compile("[-+]?[0-9]*\\.[0-9]+");
    private static final Pattern ROUGH_NUMERIC_PATTERN = Pattern.compile("[-+]?[0-9]*\\.?[0-9]*[dDbBfFlLsS]?");

    private final String input;
    private int pos;

    private SnbtParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parses an SNBT string into a {@link JsonObject}.
     *
     * @param snbt the SNBT string to parse
     *
     * @return the parsed JSON object
     *
     * @throws NbtParseException if the input is not valid SNBT
     */
    public static JsonObject parse(String snbt) {
        if (snbt == null || snbt.isBlank()) {
            throw new NbtParseException("SNBT input is null or blank");
        }

        SnbtParser parser = new SnbtParser(snbt.trim());
        parser.skipWhitespace();

        JsonElement result = parser.parseAny();
        if (!(result instanceof JsonObject)) {
            throw new NbtParseException("SNBT root must be a compound tag, got: " + result.getClass().getSimpleName());
        }

        return (JsonObject) result;
    }

    /**
     * Attempts to parse an SNBT string, returning null if parsing fails.
     *
     * @param snbt the SNBT string to parse
     *
     * @return the parsed JSON object, or null if parsing fails
     */
    public static JsonObject tryParse(String snbt) {
        try {
            return parse(snbt);
        } catch (Exception e) {
            return null;
        }
    }

    private JsonElement parseAny() {
        skipWhitespace();

        if (isFinished()) {
            throw error("Unexpected end of input");
        }

        char c = peek();
        return switch (c) {
            case '{' -> parseCompound();
            case '[' -> parseListOrArray();
            case '"', '\'' -> new JsonPrimitive(parseQuotedString());
            default -> {
                if (isNumericStart(c)) {
                    yield parseNumericOrUnquotedString();
                }

                yield parseUnquotedValue();
            }
        };
    }

    private JsonObject parseCompound() {
        expect('{');
        JsonObject object = new JsonObject();
        skipWhitespace();

        if (!isFinished() && peek() == '}') {
            advance();
            return object;
        }

        while (!isFinished()) {
            skipWhitespace();
            String key = parseIdentifier();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            JsonElement value = parseAny();
            object.add(key, value);

            skipWhitespace();
            if (!isFinished() && peek() == ',') {
                advance();
            } else {
                break;
            }
        }

        skipWhitespace();
        expect('}');
        return object;
    }

    private JsonElement parseListOrArray() {
        expect('[');
        skipWhitespace();

        // Empty list
        if (!isFinished() && peek() == ']') {
            advance();
            return new JsonArray();
        }

        // Check for typed array prefix: [B; , [I; , [L;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == ';') {
            char typeChar = input.charAt(pos);
            if (typeChar == 'B' || typeChar == 'I' || typeChar == 'L') {
                pos += 2; // skip "X;"
                return parseTypedArray();
            }
        }

        return parseList();
    }

    private JsonArray parseList() {
        JsonArray array = new JsonArray();
        boolean indexed = false;
        boolean first = true;

        while (!isFinished()) {
            skipWhitespace();

            if (peek() == ']') {
                advance();
                return array;
            }

            // Detect indexed format: [0: value, 1: value]
            if (first) {
                int savedPos = pos;
                String maybeIndex = consumeWhile(ch -> ch >= '0' && ch <= '9');
                skipWhitespace();
                if (!isFinished() && peek() == ':' && !maybeIndex.isEmpty()) {
                    indexed = true;
                    advance(); // skip ':'
                    skipWhitespace();
                } else {
                    pos = savedPos; // rewind
                }
                first = false;
            } else if (indexed) {
                // Skip index and colon for subsequent entries
                consumeWhile(ch -> ch >= '0' && ch <= '9');
                skipWhitespace();
                if (!isFinished() && peek() == ':') {
                    advance();
                    skipWhitespace();
                }
            }

            array.add(parseAny());
            skipWhitespace();

            if (!isFinished() && peek() == ',') {
                advance();
            } else {
                break;
            }
        }

        skipWhitespace();
        expect(']');
        return array;
    }

    private JsonArray parseTypedArray() {
        JsonArray array = new JsonArray();
        skipWhitespace();

        while (!isFinished()) {
            skipWhitespace();

            if (peek() == ']') {
                advance();
                return array;
            }

            array.add(parseAny());
            skipWhitespace();

            if (!isFinished() && peek() == ',') {
                advance();
            } else {
                break;
            }
        }

        skipWhitespace();
        expect(']');
        return array;
    }

    private String parseQuotedString() {
        char quoteChar = peek();
        if (quoteChar != '"' && quoteChar != '\'') {
            throw error("Expected quote character, got '" + quoteChar + "'");
        }
        advance();

        StringBuilder sb = new StringBuilder();
        boolean escaped = false;

        while (!isFinished()) {
            char c = peek();
            advance();

            if (escaped) {
                sb.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == quoteChar) {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }

        throw error("Unterminated string starting at position " + pos);
    }

    private JsonElement parseNumericOrUnquotedString() {
        String raw = consumeUnquotedValue();

        if (raw.isEmpty()) {
            throw error("Expected value");
        }

        // Try to parse as a typed numeric
        JsonElement numeric = tryParseNumeric(raw);
        if (numeric != null) {
            return numeric;
        }

        // Not a number - treat as unquoted string
        return new JsonPrimitive(raw);
    }

    private JsonElement parseUnquotedValue() {
        String raw = consumeUnquotedValue();

        if (raw.isEmpty()) {
            throw error("Expected value at position " + pos);
        }

        // Handle boolean literals
        if (raw.equalsIgnoreCase("true")) {
            return new JsonPrimitive(true);
        }
        if (raw.equalsIgnoreCase("false")) {
            return new JsonPrimitive(false);
        }

        // Try numeric
        JsonElement numeric = tryParseNumeric(raw);
        if (numeric != null) {
            return numeric;
        }

        // Unquoted string
        return new JsonPrimitive(raw);
    }

    /**
     * Consumes an unquoted value, handling namespaced identifiers like {@code minecraft:stone}.
     * A colon is treated as part of the value if followed by a letter or underscore (namespace separator),
     * and as a structural separator otherwise.
     */
    private String consumeUnquotedValue() {
        int start = pos;
        while (!isFinished()) {
            char c = input.charAt(pos);

            if (c == ':') {
                // Lookahead: colon is part of a namespace if followed by a letter or underscore
                if (pos + 1 < input.length()) {
                    char next = input.charAt(pos + 1);
                    if (Character.isLetter(next) || next == '_') {
                        pos++;
                        continue;
                    }
                }
                break; // structural separator
            }

            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }

            pos++;
        }
        return input.substring(start, pos);
    }

    private JsonElement tryParseNumeric(String raw) {
        if (!ROUGH_NUMERIC_PATTERN.matcher(raw).matches()) {
            return null;
        }

        if (FLOAT_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Float.parseFloat(raw.substring(0, raw.length() - 1)));
        }

        if (BYTE_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Byte.parseByte(raw.substring(0, raw.length() - 1)));
        }

        if (LONG_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Long.parseLong(raw.substring(0, raw.length() - 1)));
        }

        if (SHORT_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Short.parseShort(raw.substring(0, raw.length() - 1)));
        }

        if (DOUBLE_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Double.parseDouble(raw.substring(0, raw.length() - 1)));
        }

        if (INTEGER_PATTERN.matcher(raw).matches()) {
            try {
                return new JsonPrimitive(Integer.parseInt(raw));
            } catch (NumberFormatException e) {
                try {
                    return new JsonPrimitive(Long.parseLong(raw));
                } catch (NumberFormatException e2) {
                    return null;
                }
            }
        }

        if (UNTYPED_DOUBLE_PATTERN.matcher(raw).matches()) {
            return new JsonPrimitive(Double.parseDouble(raw));
        }

        return null;
    }

    private String parseIdentifier() {
        if (!isFinished() && (peek() == '"' || peek() == '\'')) {
            return parseQuotedString();
        }

        // Consume identifier characters, handling namespaced keys like "minecraft:lore".
        // A colon is part of the key if the character after it is a letter or underscore
        // (i.e. part of a namespace). It's the key-value separator if followed by whitespace,
        // a quote, a digit, a sign, or a structural character like { [ etc.
        int start = pos;
        while (!isFinished()) {
            char c = input.charAt(pos);

            if (c == ':') {
                // Look ahead to determine if this colon is the key-value separator or part of the key
                if (pos + 1 >= input.length() || isValueStartOrWhitespace(input.charAt(pos + 1))) {
                    break; // key-value separator
                }
                // Part of a namespaced key (e.g., "minecraft:lore")
                pos++;
                continue;
            }

            if (c == '}' || c == '{' || c == '[' || c == ']' || c == ',' || Character.isWhitespace(c)) {
                break;
            }

            pos++;
        }

        String ident = input.substring(start, pos);
        if (ident.isEmpty()) {
            throw error("Expected identifier");
        }

        return ident;
    }

    /**
     * Returns true if the character indicates the start of a value or is whitespace,
     * meaning the preceding colon is a key-value separator rather than part of a namespaced key.
     */
    private boolean isValueStartOrWhitespace(char c) {
        return Character.isWhitespace(c)
            || c == '{' || c == '[' || c == '"' || c == '\''
            || c == '-' || c == '+' || (c >= '0' && c <= '9');
    }

    private char peek() {
        return input.charAt(pos);
    }

    private void advance() {
        pos++;
    }

    private boolean isFinished() {
        return pos >= input.length();
    }

    private void skipWhitespace() {
        while (!isFinished() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private void expect(char expected) {
        if (isFinished()) {
            throw error("Expected '" + expected + "' but reached end of input");
        }

        if (peek() != expected) {
            throw error("Expected '" + expected + "' but got '" + peek() + "'");
        }

        advance();
    }

    private String consumeWhile(Predicate<Character> predicate) {
        int start = pos;

        while (!isFinished() && predicate.test(input.charAt(pos))) {
            pos++;
        }

        return input.substring(start, pos);
    }

    private boolean isStructuralChar(char c) {
        return c == ',' || c == '}' || c == ']' || c == ':' || Character.isWhitespace(c);
    }

    private boolean isNumericStart(char c) {
        return (c >= '0' && c <= '9') || c == '-' || c == '+';
    }

    private NbtParseException error(String message) {
        int contextStart = Math.max(0, pos - 20);
        int contextEnd = Math.min(input.length(), pos + 20);
        String context = input.substring(contextStart, contextEnd);
        String pointer = " ".repeat(Math.min(pos, 20)) + "^";

        return new NbtParseException(message + " (at position " + pos + ")\n..." + context + "...\n" + pointer);
    }
}
