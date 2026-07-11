package net.aerh.imagegenerator.parser;

import java.util.List;
import java.util.regex.Pattern;

public interface Parser<T> {

    /**
     * Matches placeholders like %%name:extra%%. Percents are paired off as {@code %%} delimiters,
     * so a {@code %} joins the value only when it begins an odd-length run of percents (the lone
     * leftover after the closing {@code %%}). This keeps values that end in a percent, e.g.
     * {@code %%health:50%%%} yielding {@code 50%}, while letting adjacent placeholders such as
     * {@code %%left_arrow%%%%left_arrow%%} split cleanly instead of the first value swallowing the
     * second placeholder's opening {@code %%}.
     */
    Pattern VARIABLE_PATTERN = Pattern.compile("%%([a-zA-Z_]+):?((?:[^%]|%(?=(?:%%)*(?!%)))*)%%", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /**
     * Reserved placeholder name for icon-only references: {@code %%icon:<name>%%} renders just the
     * icon character of the named stat, icon, or flavor entry (optionally repeated, e.g.
     * {@code %%icon:health:3%%}). No registry entry may claim this name.
     */
    String ICON_REFERENCE_NAME = "icon";

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
