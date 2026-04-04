package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
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
public class PowerStrength {

    private static final DataRegistry<PowerStrength> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<PowerStrength[]> getArrayType() {
            return PowerStrength[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/power_strengths.json";
        }

        @Override
        protected String getExternalFileName() {
            return "power_strengths.json";
        }

        @Override
        protected Function<PowerStrength, String> getNameExtractor() {
            return PowerStrength::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load power strength data", e);
        }
    }

    private final String name;
    private final String display;
    private final boolean stone;

    public static PowerStrength byName(String name) {
        return REGISTRY.findFirst(ps -> ps.getDisplay().equalsIgnoreCase(name)).orElse(null);
    }

    public static List<String> getPowerStrengthNames() {
        return REGISTRY.getAll().stream().map(PowerStrength::getName).collect(Collectors.toList());
    }

    public String getFormattedDisplay() {
        return display + (stone ? " Stone" : "") + " Power";
    }
}
