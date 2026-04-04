package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;

import java.io.IOException;
import java.util.function.Function;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class ParseType {

    private static final DataRegistry<ParseType> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<ParseType[]> getArrayType() {
            return ParseType[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/parse_types.json";
        }

        @Override
        protected String getExternalFileName() {
            return "parse_types.json";
        }

        @Override
        protected Function<ParseType, String> getNameExtractor() {
            return ParseType::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
            log.info("Loaded {} parse types!", REGISTRY.size());
        } catch (IOException e) {
            log.error("Failed to load parse type data", e);
        }
    }

    private String name;
    private String formatWithDetails;
    private String formatWithoutDetails;

    public static ParseType byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }
}
