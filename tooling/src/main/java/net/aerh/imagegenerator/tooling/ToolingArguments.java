package net.aerh.imagegenerator.tooling;

/** Apply directory options before any ToolingConstants paths are initialized. */
public final class ToolingArguments {
    private ToolingArguments() {}

    public static void configureDirectories(String[] args) {
        for (String arg : args) {
            String property = null;
            if (arg.startsWith("--work-dir=")) property = "generator.tooling.workDir";
            else if (arg.startsWith("--output-dir=")) property = "generator.tooling.outputDir";
            else if (arg.startsWith("--renderer-dir=")) property = "generator.tooling.rendererDir";
            if (property != null) {
                String value = arg.substring(arg.indexOf('=') + 1);
                if (value.isBlank()) throw new IllegalArgumentException("Empty directory option: " + arg);
                System.setProperty(property, value);
            }
        }
    }
}
