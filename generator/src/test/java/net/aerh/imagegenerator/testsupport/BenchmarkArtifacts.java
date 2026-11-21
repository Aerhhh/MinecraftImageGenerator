package net.aerh.imagegenerator.testsupport;

import net.aerh.imagegenerator.item.GeneratedObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class BenchmarkArtifacts {

    private BenchmarkArtifacts() {
    }

    public static Path storeBenchmarkGif(String filename, GeneratedObject generated) throws IOException {
        Path benchmarkDir = Files.createDirectories(Path.of("target", "benchmarks"));
        Path output = benchmarkDir.resolve(filename);
        Files.write(output, generated.getGifData());
        return output;
    }
}
