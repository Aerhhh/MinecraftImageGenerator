package net.aerh.imagegenerator.parser.text;

import net.aerh.imagegenerator.parser.StringParser;
import net.aerh.imagegenerator.text.LegacyCode;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ColorCodeParser implements StringParser {

    private static final Map<LegacyCode, Pattern> PATTERNS = Arrays.stream(LegacyCode.VALUES)
        .collect(Collectors.toMap(
            code -> code,
            code -> Pattern.compile("%%" + code.name() + "%%", Pattern.CASE_INSENSITIVE)
        ));

    private static final Pattern HEX_PATTERN = Pattern.compile("%%(#[0-9a-fA-F]{6})%%");

    @Override
    public String parse(String input) {
        for (LegacyCode value : LegacyCode.VALUES) {
            Pattern pattern = PATTERNS.get(value);
            input = pattern.matcher(input).replaceAll(String.valueOf(LegacyCode.AMPERSAND_SYMBOL) + value.getCode());
        }

        return HEX_PATTERN.matcher(input).replaceAll(LegacyCode.AMPERSAND_SYMBOL + "$1");
    }
}