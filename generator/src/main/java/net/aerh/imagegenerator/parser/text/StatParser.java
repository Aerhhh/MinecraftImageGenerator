package net.aerh.imagegenerator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.ParseType;
import net.aerh.imagegenerator.data.Stat;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.parser.StringParser;
import net.aerh.imagegenerator.text.LegacyCode;

import java.util.regex.Matcher;

@Slf4j
public class StatParser implements StringParser {

    @Override
    public String parse(String input) {
        return parse(input, ParseContext.empty());
    }

    @Override
    public String parse(String input, ParseContext context) {
        if (input == null || input.isBlank()) {
            return input;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(input);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String statName = matcher.group(1);
            String extraData = matcher.group(2);
            Stat stat = Stat.byName(statName);

            if (stat == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            ParseType parseType = ParseType.byName(stat.getParseType());
            if (parseType == null) {
                log.warn("Could not find parse type '{}' for stat '{}'", stat.getParseType(), stat.getName());
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String formatted = ParseTypeFormatter.format(stat, parseType, extraData, context);

            if (!formatted.startsWith("[")) {
                formatted += String.valueOf(LegacyCode.SECTION_SYMBOL) + LegacyCode.RESET.getCode();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
