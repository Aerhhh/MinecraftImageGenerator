package net.aerh.imagegenerator.data;

import com.google.gson.GsonBuilder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.json.serializer.ColorDeserializer;
import net.hypixel.nerdbot.marmalade.registry.DataRegistry;

import java.awt.Color;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;

@Slf4j
@Getter
@Setter
@AllArgsConstructor
@ToString
public class Gemstone {

    private static final DataRegistry<Gemstone> REGISTRY = new DataRegistry<>() {
        @Override
        protected Class<Gemstone[]> getArrayType() {
            return Gemstone[].class;
        }

        @Override
        protected String getResourcePath() {
            return "data/gemstones.json";
        }

        @Override
        protected String getExternalFileName() {
            return "gemstones.json";
        }

        @Override
        protected Function<Gemstone, String> getNameExtractor() {
            return Gemstone::getName;
        }

        @Override
        protected UnaryOperator<GsonBuilder> customizeGson() {
            return builder -> builder.registerTypeAdapter(Color.class, new ColorDeserializer());
        }
    };

    static {
        try {
            REGISTRY.load();
        } catch (IOException e) {
            log.error("Failed to load gemstone data", e);
        }
    }

    private String name;
    private String icon;
    private String formattedIcon;
    private Color color;
    private Map<String, String> formattedTiers;

    public static Gemstone byName(String name) {
        return REGISTRY.byName(name).orElse(null);
    }

    public static List<Gemstone> getGemstones() {
        return REGISTRY.getAll();
    }
}
