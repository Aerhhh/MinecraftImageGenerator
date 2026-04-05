package net.aerh.jigsaw.core.nbt;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.aerh.jigsaw.exception.ParseException;

import java.util.Map;

/**
 * A recursive descent parser for Stringified NBT (SNBT) that converts it to a {@link JsonElement}.
 * <p>
 * Supported constructs:
 * <ul>
 *   <li>Compounds: {@code {key:value, ...}}</li>
 *   <li>Lists: {@code [val, val, ...]}</li>
 *   <li>Typed arrays: {@code [B;...]} (byte), {@code [I;...]} (int), {@code [L;...]} (long)</li>
 *   <li>Indexed list format: {@code [0:val, 1:val, ...]}</li>
 *   <li>Quoted strings (single or double quotes)</li>
 *   <li>Unquoted strings / barewords</li>
 *   <li>Numeric suffixes: {@code b/B} (byte), {@code s/S} (short), {@code l/L} (long),
 *       {@code f/F} (float), {@code d/D} (double)</li>
 *   <li>Booleans: {@code true} maps to {@code 1}, {@code false} maps to {@code 0}</li>
 * </ul>
 */
public final class SnbtParser {

    private final String input;
    private int pos;

    private SnbtParser(String input) {
        this.input = input;
        this.pos = 0;
    }

    /**
     * Parses the given SNBT string and returns its {@link JsonElement} representation.
     *
     * @param snbt The SNBT input.
     * @return The parsed {@link JsonElement}.
     * @throws ParseException if the input is null, blank, or syntactically invalid.
     */
    public static JsonElement parse(String snbt) throws ParseException {
        if (snbt == null || snbt.isBlank()) {
            throw new ParseException("SNBT input must not be null or blank",
                    Map.of("input", String.valueOf(snbt)));
        }
        SnbtParser parser = new SnbtParser(snbt.strip());
        try {
            JsonElement result = parser.parseValue();
            parser.skipWhitespace();
            if (parser.pos < parser.input.length()) {
                throw new ParseException("Unexpected trailing content at position " + parser.pos,
                        Map.of("input", snbt, "position", parser.pos));
            }
            return result;
        } catch (ParseException e) {
            throw e;
        } catch (Exception e) {
            throw new ParseException("Failed to parse SNBT: " + e.getMessage(),
                    Map.of("input", snbt), e);
        }
    }

    // --- core parsing ---

    private JsonElement parseValue() throws ParseException {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new ParseException("Unexpected end of input at position " + pos,
                    Map.of("input", input));
        }

        char c = peek();
        if (c == '{') {
            return parseCompound();
        } else if (c == '[') {
            return parseList();
        } else if (c == '"' || c == '\'') {
            return new JsonPrimitive(parseQuotedString());
        } else {
            return parsePrimitive();
        }
    }

    private JsonObject parseCompound() throws ParseException {
        expect('{');
        JsonObject obj = new JsonObject();

        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return obj;
        }

        while (true) {
            skipWhitespace();
            String key = parseKey();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            JsonElement value = parseValue();
            obj.add(key, value);

            skipWhitespace();
            char next = peek();
            if (next == '}') {
                pos++;
                break;
            } else if (next == ',') {
                pos++;
            } else {
                throw new ParseException("Expected ',' or '}' at position " + pos + " but got '" + next + "'",
                        Map.of("input", input, "position", pos));
            }
        }

        return obj;
    }

    private JsonElement parseList() throws ParseException {
        expect('[');
        skipWhitespace();
        JsonArray array = new JsonArray();

        // Check for typed array prefix: [B;, [I;, [L;
        if (pos + 1 < input.length() && input.charAt(pos + 1) == ';') {
            char typeChar = input.charAt(pos);
            if (typeChar == 'B' || typeChar == 'I' || typeChar == 'L') {
                pos += 2; // skip type char and semicolon
                skipWhitespace();
                if (peek() == ']') {
                    pos++;
                    return array;
                }
                parseListElements(array);
                return array;
            }
        }

        if (peek() == ']') {
            pos++;
            return array;
        }

        // Check for indexed format: [0:val, 1:val]
        int savedPos = pos;
        if (looksLikeIndexedList()) {
            pos = savedPos;
            parseIndexedListElements(array);
        } else {
            pos = savedPos;
            parseListElements(array);
        }

        return array;
    }

    private boolean looksLikeIndexedList() {
        int savedPos = pos;
        skipWhitespace();
        // Try to read digits followed by ':'
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            pos++;
        }
        if (pos > start) {
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ':') {
                pos = savedPos;
                return true;
            }
        }
        pos = savedPos;
        return false;
    }

    private void parseListElements(JsonArray array) throws ParseException {
        while (true) {
            skipWhitespace();
            JsonElement element = parseValue();
            array.add(element);

            skipWhitespace();
            char next = peek();
            if (next == ']') {
                pos++;
                break;
            } else if (next == ',') {
                pos++;
            } else {
                throw new ParseException("Expected ',' or ']' at position " + pos + " but got '" + next + "'",
                        Map.of("input", input, "position", pos));
            }
        }
    }

    private void parseIndexedListElements(JsonArray array) throws ParseException {
        while (true) {
            skipWhitespace();
            // Skip index (digits) and colon
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                pos++;
            }
            skipWhitespace();
            expect(':');
            skipWhitespace();
            JsonElement element = parseValue();
            array.add(element);

            skipWhitespace();
            char next = peek();
            if (next == ']') {
                pos++;
                break;
            } else if (next == ',') {
                pos++;
            } else {
                throw new ParseException("Expected ',' or ']' at position " + pos + " but got '" + next + "'",
                        Map.of("input", input, "position", pos));
            }
        }
    }

    private String parseKey() throws ParseException {
        skipWhitespace();
        if (pos >= input.length()) {
            throw new ParseException("Expected key at position " + pos, Map.of("input", input));
        }
        char c = peek();
        if (c == '"' || c == '\'') {
            return parseQuotedString();
        }
        // Handle namespaced keys like "minecraft:lore" where the colon is part of the key,
        // not the key-value separator. A colon is part of the key if the character after it
        // is a letter or underscore AND there is another colon before the next comma/closing brace
        // (i.e. there's still a key-value separator to come). Otherwise it's the separator.
        int start = pos;
        while (pos < input.length()) {
            char ch = input.charAt(pos);
            if (ch == ':') {
                // Look ahead: is there another ':' before the next ',' or '}'?
                // If so, this colon is part of a namespaced key.
                if (hasAnotherColonBeforeEndOfEntry(pos + 1)) {
                    pos++;
                    continue;
                }
                break; // key-value separator
            }
            if (ch == '}' || ch == '{' || ch == '[' || ch == ']' || ch == ',' || Character.isWhitespace(ch)) {
                break;
            }
            pos++;
        }
        String key = input.substring(start, pos);
        if (key.isEmpty()) {
            throw new ParseException("Expected key at position " + pos, Map.of("input", input));
        }
        return key;
    }

    /**
     * Scans forward from the given position looking for another ':' before hitting
     * a ',' or '}' (at the same nesting level). Skips over nested compounds/lists/strings.
     */
    private boolean hasAnotherColonBeforeEndOfEntry(int from) {
        int depth = 0;
        for (int i = from; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '"' || ch == '\'') {
                // Skip quoted string
                i++;
                while (i < input.length() && input.charAt(i) != ch) {
                    if (input.charAt(i) == '\\') i++;
                    i++;
                }
                continue;
            }
            if (ch == '{' || ch == '[') {
                depth++;
            } else if (ch == '}' || ch == ']') {
                if (depth == 0) return false;
                depth--;
            } else if (ch == ',' && depth == 0) {
                return false;
            } else if (ch == ':' && depth == 0) {
                return true;
            }
        }
        return false;
    }

    private String parseQuotedString() throws ParseException {
        char quote = input.charAt(pos++);
        StringBuilder sb = new StringBuilder();
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                pos++;
                char escaped = input.charAt(pos);
                sb.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> escaped;
                });
                pos++;
            } else if (c == quote) {
                pos++;
                return sb.toString();
            } else {
                sb.append(c);
                pos++;
            }
        }
        throw new ParseException("Unterminated string at position " + pos,
                Map.of("input", input));
    }

    private String parseBareword() {
        int start = pos;
        while (pos < input.length() && isBarewordChar(input.charAt(pos))) {
            pos++;
        }
        return input.substring(start, pos);
    }

    private JsonElement parsePrimitive() throws ParseException {
        String raw = parseBareword();
        if (raw.isEmpty()) {
            throw new ParseException("Expected value at position " + pos,
                    Map.of("input", input, "position", pos));
        }

        // Booleans
        if (raw.equalsIgnoreCase("true")) {
            return new JsonPrimitive(1);
        }
        if (raw.equalsIgnoreCase("false")) {
            return new JsonPrimitive(0);
        }

        // Numeric with suffix
        if (raw.length() > 1) {
            char suffix = raw.charAt(raw.length() - 1);
            String num = raw.substring(0, raw.length() - 1);
            try {
                JsonElement suffixed = switch (Character.toLowerCase(suffix)) {
                    case 'b' -> new JsonPrimitive(Byte.parseByte(num));
                    case 's' -> new JsonPrimitive(Short.parseShort(num));
                    case 'l' -> new JsonPrimitive(Long.parseLong(num));
                    case 'f' -> new JsonPrimitive(Float.parseFloat(num));
                    case 'd' -> new JsonPrimitive(Double.parseDouble(num));
                    default -> null;
                };
                if (suffixed != null) {
                    return suffixed;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to unquoted string or integer
            }
        }

        // Plain integer
        try {
            return new JsonPrimitive(Integer.parseInt(raw));
        } catch (NumberFormatException ignored) {}

        // Plain double (decimal point present, no suffix)
        try {
            if (raw.contains(".")) {
                return new JsonPrimitive(Double.parseDouble(raw));
            }
        } catch (NumberFormatException ignored) {}

        // Unquoted string (bareword)
        return new JsonPrimitive(raw);
    }

    // --- helpers ---

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= input.length()) {
            return '\0';
        }
        return input.charAt(pos);
    }

    private void expect(char c) throws ParseException {
        skipWhitespace();
        if (pos >= input.length() || input.charAt(pos) != c) {
            char actual = pos < input.length() ? input.charAt(pos) : '\0';
            throw new ParseException(
                    "Expected '" + c + "' at position " + pos + " but got '" + actual + "'",
                    Map.of("input", input, "position", pos, "expected", String.valueOf(c)));
        }
        pos++;
    }

    private static boolean isBarewordChar(char c) {
        return c != ',' && c != '}' && c != ']' && c != ':' && !Character.isWhitespace(c);
    }
}
