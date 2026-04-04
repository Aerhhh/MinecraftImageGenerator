package net.aerh.imagegenerator.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.aerh.imagegenerator.text.ChatFormat;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Flavor implements FormattableEntry {

    private static final DataRegistry<Flavor> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Flavor[]> getArrayType() {
            return Flavor[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/flavor.json";
        }

        @Override
        protected String getExternalFileName() {
            return "flavor.json";
        }

        @Override
        protected Function<Flavor, String> getNameExtractor() {
            return Flavor::getName;
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load flavor text data", e);
        }
    }

    private String icon;
    private String name;
    private String stat;
    private String display;
    private ChatFormat color;
    @Nullable
    private ChatFormat subColor;
    private String parseType;

    public static Flavor byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Flavor> getFlavors() {
        return REGISTRY.getAll();
    }

    public ChatFormat getSecondaryColor() {
        if (subColor != null) {
            return subColor;
        } else {
            return color;
        }
    }
}