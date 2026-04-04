package net.aerh.imagegenerator.data;

import lombok.extern.slf4j.Slf4j;
import net.hypixel.nerdbot.marmalade.json.JsonLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.google.gson.GsonBuilder;

/**
 * Loads supplementary data from external JSON files and merges them into
 * the built-in data lists. External entries with the same name as a
 * built-in entry override it; new entries are appended.
 * <p>
 * External files are discovered in this order:
 * <ol>
 *   <li>The directory specified by the system property {@code generator.data.dir}</li>
 *   <li>A {@code data/} directory relative to the working directory</li>
 * </ol>
 * External files must use the same name as the built-in file
 * (e.g. {@code stats.json}, {@code flavor.json}).
 */
@Slf4j
public final class ExternalDataLoader {

    private static final String DATA_DIR_PROPERTY = "generator.data.dir";
    private static final String DEFAULT_DATA_DIR = "data";

    private ExternalDataLoader() {
    }

    /**
     * Merges entries from an external JSON file into the target list.
     *
     * @param target        the mutable list to merge into (already populated with built-in data)
     * @param arrayType     the array class for Gson deserialization (e.g. {@code Stat[].class})
     * @param fileName      the JSON file name (e.g. {@code "stats.json"})
     * @param nameExtractor function to extract the name/key from an entry for override matching
     */
    public static <T> void mergeExternal(List<T> target, Class<T[]> arrayType,
                                         String fileName, Function<T, String> nameExtractor) {
        mergeExternal(target, arrayType, fileName, nameExtractor, builder -> builder);
    }

    /**
     * Merges entries from an external JSON file into the target list, using a
     * custom Gson configurator for types that need special deserialization.
     *
     * @param target           the mutable list to merge into
     * @param arrayType        the array class for Gson deserialization
     * @param fileName         the JSON file name
     * @param nameExtractor    function to extract the name/key from an entry
     * @param gsonConfigurator customizes the GsonBuilder (e.g. register type adapters)
     */
    public static <T> void mergeExternal(List<T> target, Class<T[]> arrayType,
                                         String fileName, Function<T, String> nameExtractor,
                                         UnaryOperator<GsonBuilder> gsonConfigurator) {
        Path externalFile = resolveExternalFile(fileName);
        if (externalFile == null) {
            return;
        }

        try {
            List<T> external = JsonLoader.loadFromJson(
                arrayType,
                externalFile.toUri().toURL(),
                gsonConfigurator
            );

            int overridden = 0;
            int added = 0;

            for (T entry : external) {
                String name = nameExtractor.apply(entry);
                boolean existed = target.removeIf(
                    existing -> nameExtractor.apply(existing).equalsIgnoreCase(name)
                );
                target.add(entry);

                if (existed) {
                    overridden++;
                } else {
                    added++;
                }
            }

            log.info("Merged external '{}': {} overridden, {} added (from '{}')",
                fileName, overridden, added, externalFile);
        } catch (Exception e) {
            log.error("Failed to load external data from '{}'", externalFile, e);
        }
    }

    private static Path resolveExternalFile(String fileName) {
        String customDir = System.getProperty(DATA_DIR_PROPERTY);
        if (customDir != null && !customDir.isBlank()) {
            Path file = Path.of(customDir, fileName);
            if (Files.isRegularFile(file)) {
                return file;
            }
            log.debug("External data file not found at configured path: {}", file);
        }

        Path file = Path.of(DEFAULT_DATA_DIR, fileName);
        if (Files.isRegularFile(file)) {
            return file;
        }

        return null;
    }
}
