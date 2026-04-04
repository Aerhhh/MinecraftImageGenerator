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

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Icon {

    private static final DataRegistry<Icon> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Icon[]> getArrayType() {
            return Icon[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/icons.json";
        }

        @Override
        protected String getExternalFileName() {
            return "icons.json";
        }

        @Override
        protected Function<Icon, String> getNameExtractor() {
            return Icon::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load icon data", e);
        }
    }

    private String name;
    private String icon;

    public static Icon byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Icon> getIcons() {
        return REGISTRY.getAll();
    }
}
