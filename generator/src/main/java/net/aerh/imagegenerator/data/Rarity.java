package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.text.ChatFormat;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Rarity {

    private static final DataRegistry<Rarity> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Rarity[]> getArrayType() {
            return Rarity[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/rarities.json";
        }

        @Override
        protected String getExternalFileName() {
            return "rarities.json";
        }

        @Override
        protected Function<Rarity, String> getNameExtractor() {
            return Rarity::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load rarity data", e);
        }
    }

    private final String name;
    private final String display;
    private final ChatFormat color;

    public static Rarity byName(String name) {
        return REGISTRY.findFirst(rarity -> rarity.getDisplay().equalsIgnoreCase(name)).orElse(null);
    }

    public static List<String> getRarityNames() {
        return REGISTRY.getAll().stream().map(Rarity::getName).collect(Collectors.toList());
    }

    public static List<Rarity> getAllRarities() {
        return REGISTRY.getAll();
    }

    public String getColorCode() {
        return String.valueOf(ChatFormat.AMPERSAND_SYMBOL) + color.getCode();
    }

    public String getFormattedDisplay() {
        return getColorCode() + ChatFormat.AMPERSAND_SYMBOL + ChatFormat.BOLD.getCode() + display;
    }
}
