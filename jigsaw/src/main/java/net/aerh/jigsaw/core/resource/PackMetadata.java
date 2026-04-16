package net.aerh.jigsaw.core.resource;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Parsed metadata from a resource pack's {@code pack.mcmeta} file.
 *
 * @param packFormat  the declared pack format version
 * @param description the human-readable pack description
 */
public record PackMetadata(int packFormat, String description) {

    private static final Gson GSON = new Gson();

    public PackMetadata {
        if (packFormat < 0) {
            throw new IllegalArgumentException("packFormat must be >= 0, got: " + packFormat);
        }
        if (description == null) {
            description = "";
        }
    }

    /**
     * Parses a {@link PackMetadata} from the raw JSON string of a {@code pack.mcmeta} file.
     *
     * <p>Handles the {@code description} field being a plain string, a JSON text component
     * object, or a JSON text component array (all valid per the Minecraft resource pack spec).
     *
     * @param json the raw JSON content of pack.mcmeta
     * @return the parsed metadata
     * @throws IllegalArgumentException if the JSON is malformed or missing required fields
     */
    public static PackMetadata fromJson(String json) {
        JsonObject root = GSON.fromJson(json, JsonObject.class);
        JsonObject pack = root.getAsJsonObject("pack");
        if (pack == null) {
            throw new IllegalArgumentException("pack.mcmeta missing 'pack' object");
        }
        int format = pack.get("pack_format").getAsInt();
        String desc = extractDescription(pack);
        return new PackMetadata(format, desc);
    }

    /**
     * Extracts a plain-text description from the {@code pack} object.
     *
     * <p>The {@code description} field can be:
     * <ul>
     *   <li>A plain string: {@code "My pack"}</li>
     *   <li>A JSON text component object: {@code {"text": "My pack", "color": "gold"}}</li>
     *   <li>A JSON text component array: {@code [{"text": "Line 1"}, " ", {"text": "Line 2"}]}</li>
     * </ul>
     *
     * <p>For text components, only the raw {@code text} values are extracted (formatting is
     * stripped since it's not meaningful outside of Minecraft's renderer).
     */
    private static String extractDescription(JsonObject pack) {
        if (!pack.has("description")) {
            return "";
        }
        JsonElement desc = pack.get("description");
        if (desc.isJsonPrimitive()) {
            return desc.getAsString();
        }
        if (desc.isJsonObject()) {
            return extractTextFromComponent(desc.getAsJsonObject());
        }
        if (desc.isJsonArray()) {
            return extractTextFromArray(desc.getAsJsonArray());
        }
        return "";
    }

    /**
     * Extracts plain text from a single text component object, including its {@code extra} children.
     */
    private static String extractTextFromComponent(JsonObject component) {
        StringBuilder sb = new StringBuilder();
        if (component.has("text")) {
            sb.append(component.get("text").getAsString());
        } else if (component.has("translate")) {
            sb.append(component.get("translate").getAsString());
        }
        if (component.has("extra")) {
            sb.append(extractTextFromArray(component.getAsJsonArray("extra")));
        }
        return sb.toString();
    }

    /**
     * Extracts plain text from a text component array. Each element may be a string or a
     * text component object.
     */
    private static String extractTextFromArray(JsonArray array) {
        StringBuilder sb = new StringBuilder();
        for (JsonElement element : array) {
            if (element.isJsonPrimitive()) {
                sb.append(element.getAsString());
            } else if (element.isJsonObject()) {
                sb.append(extractTextFromComponent(element.getAsJsonObject()));
            }
        }
        return sb.toString();
    }
}
