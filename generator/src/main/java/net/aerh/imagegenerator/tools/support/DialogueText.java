package net.aerh.imagegenerator.tools.support;

import net.aerh.imagegenerator.exception.GeneratorException;

/**
 * Builds NPC dialogue lore. Input lines are separated by the literal two characters {@code \n};
 * output lines are separated by real newlines. A line may end with {@code {options: A, B}} to add
 * a selectable options line under it.
 */
public final class DialogueText {

    private DialogueText() {
    }

    /** Dialogue spoken by one NPC. */
    public static String buildSingle(String npcName, String dialogue, boolean abiphone) {
        String[] lines = dialogue.split("\\\\n");

        for (int i = 0; i < lines.length; i++) {
            lines[i] = expandOptions(npcLine(npcName, abiphone, lines[i]), i + 1);
        }

        return String.join("\n", lines);
    }

    /**
     * Dialogue spoken by several NPCs. {@code npcNames} is comma separated; every dialogue line
     * starts with the zero based index of its speaker and a comma, for example {@code 0, Hello!}.
     * An index past the last name uses the last name.
     *
     * @throws GeneratorException if a line has no index, a non numeric index, or a negative index
     */
    public static String buildMulti(String npcNames, String dialogue, boolean abiphone) {
        String[] lines = dialogue.split("\\\\n");
        String[] names = npcNames.split(", ?");

        for (int i = 0; i < lines.length; i++) {
            String[] split = lines[i].split(", ?", 2);

            if (split.length < 2) {
                throw new GeneratorException("Each line must start with an NPC index followed by a comma (line " + (i + 1) + ")! Example: 0, Hello!");
            }

            int index;
            try {
                index = Integer.parseInt(split[0]);
            } catch (NumberFormatException exception) {
                throw new GeneratorException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
            }

            if (index < 0) {
                throw new GeneratorException("Invalid NPC name index found in dialogue: " + split[0] + " (line " + (i + 1) + ")");
            }

            if (index >= names.length) {
                index = names.length - 1;
            }

            lines[i] = expandOptions(npcLine(names[index], abiphone, split[1]), i + 1);
        }

        return String.join("\n", lines);
    }

    private static String npcLine(String npcName, boolean abiphone, String text) {
        return "&e[NPC] " + npcName + "&f: " + (abiphone ? "&b%%ABIPHONE%%&f " : "") + text;
    }

    private static String expandOptions(String line, int lineNumber) {
        if (!line.contains("{options:")) {
            return line;
        }

        String[] split = line.split("\\{options: ?");

        if (split.length < 2 || split[1].replace("}", "").isBlank()) {
            throw new GeneratorException("Malformed {options: ...} block in dialogue (line " + lineNumber + ")! Expected format: {options: Option 1, Option 2}");
        }

        StringBuilder result = new StringBuilder(split[0]).append("\n&eSelect an option: &f");

        for (String option : split[1].replace("}", "").split(", ?")) {
            result.append("&a").append(option).append("&f ");
        }

        return result.toString();
    }
}
