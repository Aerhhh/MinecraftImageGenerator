package net.aerh.jigsaw.core.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Parses an inventory string into a list of {@link InventoryItem} instances.
 *
 * <h3>Format</h3>
 * <p>An inventory string is a {@code %%}-delimited sequence of item tokens, where each token is:
 * <pre>
 *   material[,modifier1[,modifier2[,...]]]:slotSpec
 * </pre>
 *
 * <h4>Material and modifiers</h4>
 * <ul>
 *   <li>{@code material} - the Minecraft item/block ID (e.g. {@code diamond_sword})</li>
 *   <li>Optional comma-separated modifiers after the material:
 *     <ul>
 *       <li>{@code enchant} - marks the item as enchanted</li>
 *       <li>An integer trailing modifier is treated as a durability percentage ({@code 0-100})</li>
 *       <li>Any other modifier is passed through as extra data</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h4>Slot specifications</h4>
 * <ul>
 *   <li><strong>Map syntax</strong>: {@code {slot:amount, slot2:amount2, ...}}<br>
 *       Maps specific slots to specific amounts. Slots may be ranges (e.g. {@code 1-5:3}).</li>
 *   <li><strong>Array syntax</strong>: {@code [slot1,slot2,...][amount]}<br>
 *       Places the item in multiple slots with an optional uniform amount.</li>
 *   <li><strong>Simple syntax</strong>: {@code slot} or {@code slot,amount}<br>
 *       Places the item in a single slot with an optional amount.</li>
 * </ul>
 *
 * <p>All slot and amount values are clamped to their valid ranges ({@code [1, totalSlots]} and
 * {@code [1, 64]} respectively). Slots are 1-indexed in the string but are translated to
 * 0-indexed values in the returned {@link InventoryItem} instances.
 *
 * <h3>Example</h3>
 * <pre>{@code
 * // Two diamond swords in slot 1, and a stack of 32 arrows in slot 5
 * "diamond_sword:1,2 %% arrow:5,32"
 * }</pre>
 */
final class InventoryStringParser {

    private static final int MIN_SLOT = 1;
    private static final int MIN_AMOUNT = 1;
    private static final int MAX_AMOUNT = 64;

    private final int totalSlots;

    /**
     * Creates a new parser for an inventory with the given total slot count.
     *
     * @param totalSlots the total number of inventory slots (1-indexed upper bound for parsing)
     */
    InventoryStringParser(int totalSlots) {
        if (totalSlots < 1) {
            throw new IllegalArgumentException("totalSlots must be >= 1, got: " + totalSlots);
        }
        this.totalSlots = totalSlots;
    }

    /**
     * Parses the given inventory string and returns the resulting items.
     *
     * <p>Each item token in the {@code %%}-separated string is parsed independently.
     * Multi-slot tokens (map or array syntax) are expanded into one {@link InventoryItem} per slot.
     *
     * @param input the inventory string; must not be {@code null}
     * @return the ordered list of parsed items; never {@code null}
     * @throws IllegalArgumentException if any token is malformed
     */
    List<InventoryItem> parse(String input) {
        Objects.requireNonNull(input, "input must not be null");

        String[] tokens = input.split("%%");
        List<InventoryItem> result = new ArrayList<>();

        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) {
                continue;
            }
            result.addAll(parseToken(token));
        }

        return result;
    }

    private List<InventoryItem> parseToken(String token) {
        int separatorIndex = findSlotSeparatorIndex(token);
        if (separatorIndex == -1) {
            throw new IllegalArgumentException(
                    "Invalid inventory token (missing slot separator ':'): '" + token + "'");
        }

        String materialSection = token.substring(0, separatorIndex).trim();
        String slotSection = token.substring(separatorIndex + 1).trim();

        // Parse material and optional modifiers
        ParsedMaterial parsed = parseMaterialSection(materialSection);

        // Parse slot spec into (slot, amount) pairs
        List<SlotAmount> slotAmounts;
        if (slotSection.startsWith("{")) {
            slotAmounts = parseMapSlots(slotSection);
        } else if (slotSection.startsWith("[")) {
            slotAmounts = parseArraySlots(slotSection);
        } else {
            slotAmounts = parseSimpleSlot(slotSection);
        }

        // Build InventoryItem per (slot, amount) pair; translate from 1-indexed to 0-indexed
        List<InventoryItem> items = new ArrayList<>(slotAmounts.size());
        for (SlotAmount sa : slotAmounts) {
            InventoryItem.Builder builder = InventoryItem.builder(sa.slot - 1, parsed.material)
                    .stackCount(sa.amount)
                    .enchanted(parsed.enchanted);
            if (parsed.durabilityPercent != null) {
                builder.durabilityPercent(parsed.durabilityPercent);
            }
            items.add(builder.build());
        }
        return items;
    }

    /**
     * Finds the index of the {@code :} character that separates the material from the slot data.
     * Colons that appear inside {@code {}} or {@code []} blocks are skipped, as are colons that
     * separate a Minecraft namespaced ID (i.e. the character after the colon is a letter).
     */
    private int findSlotSeparatorIndex(String token) {
        int braceDepth = 0;
        int bracketDepth = 0;

        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            switch (c) {
                case '{' -> braceDepth++;
                case '}' -> braceDepth = Math.max(0, braceDepth - 1);
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth = Math.max(0, bracketDepth - 1);
                case ':' -> {
                    if (braceDepth > 0 || bracketDepth > 0) {
                        continue;
                    }
                    // Peek at the next non-whitespace character
                    int j = i + 1;
                    while (j < token.length() && Character.isWhitespace(token.charAt(j))) {
                        j++;
                    }
                    if (j >= token.length()) {
                        return -1;
                    }
                    // A letter following ':' indicates a namespaced ID (e.g. minecraft:stone)
                    if (Character.isLetter(token.charAt(j))) {
                        continue;
                    }
                    return i;
                }
                default -> { /* nothing */ }
            }
        }
        return -1;
    }

    private ParsedMaterial parseMaterialSection(String section) {
        if (!section.contains(",")) {
            return new ParsedMaterial(section, false, null);
        }

        String[] parts = section.split(",");
        String material = parts[0].trim();
        boolean enchanted = false;
        Double durabilityPercent = null;
        List<String> extraModifiers = new ArrayList<>();

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i].trim();
            if (part.isEmpty()) {
                continue;
            }
            // Check if the last part is an integer durability
            if (i == parts.length - 1 && isInteger(part)) {
                int raw = Integer.parseInt(part);
                durabilityPercent = (double) clamp(raw, 0, 100);
            } else if (part.equalsIgnoreCase("enchant")) {
                enchanted = true;
            } else {
                extraModifiers.add(part);
            }
        }

        // Any remaining non-enchant, non-durability modifiers are ignored (they were "extra data"
        // used by the old generator's overlay system, which Jigsaw handles differently).
        return new ParsedMaterial(material, enchanted, durabilityPercent);
    }

    /**
     * Parses map-style slot spec: {@code {slot:amount, slot2:amount2}}.
     * Slots may be ranges: {@code 1-5:3}.
     */
    private List<SlotAmount> parseMapSlots(String section) {
        int end = section.indexOf('}');
        String inner = section.substring(1, end != -1 ? end : section.length()).trim();
        String[] pairs = inner.split(",");

        List<SlotAmount> result = new ArrayList<>();
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] kv = trimmed.split(":");
            if (kv.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid map slot entry (expected 'slot:amount'): '" + trimmed + "'");
            }
            int amount = clamp(parseIntStrict(kv[1].trim(), "amount"), MIN_AMOUNT, MAX_AMOUNT);
            int[] slots = parseSlotRange(kv[0].trim());
            for (int slot : slots) {
                result.add(new SlotAmount(slot, amount));
            }
        }
        return result;
    }

    /**
     * Parses array-style slot spec: {@code [slot1,slot2,...][amount]}.
     */
    private List<SlotAmount> parseArraySlots(String section) {
        int closingBracket = section.indexOf(']');
        int amount = 1;

        if (closingBracket != -1) {
            String afterBracket = section.substring(closingBracket + 1).replaceAll("[^0-9]", "");
            if (!afterBracket.isEmpty()) {
                amount = clamp(Integer.parseInt(afterBracket), MIN_AMOUNT, MAX_AMOUNT);
            }
            section = section.substring(0, closingBracket + 1);
        }

        String inner = section.substring(1, section.length() - 1).trim();
        int[] slots = parseSlotRange(inner);

        int finalAmount = amount;
        List<SlotAmount> result = new ArrayList<>(slots.length);
        for (int slot : slots) {
            result.add(new SlotAmount(slot, finalAmount));
        }
        return result;
    }

    /**
     * Parses simple slot spec: {@code slot} or {@code slot,amount}.
     */
    private List<SlotAmount> parseSimpleSlot(String section) {
        int amount = 1;
        String slotStr = section;

        if (section.contains(",")) {
            int commaIdx = section.indexOf(',');
            slotStr = section.substring(0, commaIdx).trim();
            try {
                amount = clamp(Integer.parseInt(section.substring(commaIdx + 1).trim()),
                        MIN_AMOUNT, MAX_AMOUNT);
            } catch (NumberFormatException ignored) {
                // Non-numeric amount - treat as 1
            }
        }

        int slot;
        try {
            slot = clamp(Integer.parseInt(slotStr.trim()), MIN_SLOT, totalSlots);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "Invalid slot number: '" + slotStr + "'");
        }

        return List.of(new SlotAmount(slot, amount));
    }

    /**
     * Parses a comma-separated list of slots or slot ranges (e.g. {@code "1,3-5,7"}).
     * Returns a flat array of all slot numbers, clamped to {@code [1, totalSlots]}.
     */
    private int[] parseSlotRange(String slotData) {
        String[] parts = slotData.split(",");
        IntStream.Builder builder = IntStream.builder();

        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("-")) {
                String[] range = trimmed.split("-");
                if (range.length != 2) {
                    throw new IllegalArgumentException(
                            "Invalid slot range format: '" + trimmed + "'");
                }
                int start = clamp(parseIntStrict(range[0].trim(), "start slot"), MIN_SLOT, totalSlots);
                int end = clamp(parseIntStrict(range[1].trim(), "end slot"), MIN_SLOT, totalSlots);
                if (start > end) {
                    throw new IllegalArgumentException(
                            "Range start must be <= end: '" + trimmed + "'");
                }
                IntStream.rangeClosed(start, end).forEach(builder::add);
            } else {
                builder.add(clamp(parseIntStrict(trimmed, "slot"), MIN_SLOT, totalSlots));
            }
        }

        return builder.build().toArray();
    }

    private static int parseIntStrict(String s, String label) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + label + ": '" + s + "'");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    // --- Internal value types ---

    private record ParsedMaterial(String material, boolean enchanted, Double durabilityPercent) {}

    private record SlotAmount(int slot, int amount) {}
}
