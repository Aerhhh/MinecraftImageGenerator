package net.aerh.imagegenerator.parser;

import java.util.List;
import java.util.regex.Pattern;

public interface Parser<T> {

    /**
     * Matches placeholders like %%name:extra%% and treats the last %% (not followed by another %)
     * as the closing delimiter so values can contain % characters.
     */
    Pattern VARIABLE_PATTERN = Pattern.compile("%%([a-zA-Z_]+):?(.*?)%%(?!%)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Parses a string using a list of parsers.
     *
     * @param input   The string to parse.
     * @param parsers The list of {@link Parser parsers} to use.
     * @param <T>     The type of object to parse into.
     *
     * @return The parsed object of the given {@link T type}.
     */
    static <T> T parseString(String input, List<Parser<T>> parsers) {
        return parseString(input, parsers, ParseContext.empty());
    }

    /**
     * Parses a string using a list of parsers with the given {@link ParseContext}.
     *
     * @param input   The string to parse.
     * @param parsers The list of {@link Parser parsers} to use.
     * @param context The {@link ParseContext} carrying pack-conditional state.
     * @param <T>     The type of object to parse into.
     *
     * @return The parsed object of the given {@link T type}.
     */
    static <T> T parseString(String input, List<Parser<T>> parsers, ParseContext context) {
        T result = null;

        for (Parser<T> parser : parsers) {
            result = parser.parse(result == null ? input : result.toString(), context);
        }

        return result;
    }

    /**
     * Parses a string into the given {@link T type}.
     *
     * @param input The string to parse.
     *
     * @return The parsed object of the given {@link T type}.
     */
    T parse(String input);

    /**
     * Parses a string into the given {@link T type} with a {@link ParseContext}. Parsers that
     * resolve pack-conditional data override this; the default ignores the context.
     *
     * @param input   The string to parse.
     * @param context The {@link ParseContext} carrying pack-conditional state.
     *
     * @return The parsed object of the given {@link T type}.
     */
    default T parse(String input, ParseContext context) {
        return parse(input);
    }
}
