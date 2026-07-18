package net.aerh.imagegenerator.parser.text;

import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.data.Flavor;
import net.aerh.imagegenerator.data.ParseType;
import net.aerh.imagegenerator.parser.ParseContext;
import net.aerh.imagegenerator.parser.StringParser;
import net.aerh.imagegenerator.text.LegacyCode;

import java.util.regex.Matcher;

@Slf4j
public class FlavorParser implements StringParser {

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
            String flavorName = matcher.group(1);
            String extraData = matcher.group(2);
            Flavor flavor = Flavor.byName(flavorName);

            if (flavor == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            ParseType parseType = ParseType.byName(flavor.getParseType());
            if (parseType == null) {
                log.warn("Could not find parse type '{}' for flavor '{}'", flavor.getParseType(), flavor.getName());
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            String formatted = ParseTypeFormatter.format(flavor, parseType, extraData, context);

            if (!formatted.startsWith("[")) {
                formatted += String.valueOf(LegacyCode.SECTION_SYMBOL) + LegacyCode.RESET.getCode();
            }

            matcher.appendReplacement(result, Matcher.quoteReplacement(formatted));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
