package net.aerh.imagegenerator.testsupport;

import net.aerh.imagegenerator.item.GeneratedObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public final class BenchmarkArtifacts {

    private BenchmarkArtifacts() {
    }

    @FunctionalInterface
    public interface BenchmarkGenerator {
        GeneratedObject generate() throws IOException;
    }

    public static void runBenchmark(
        String label,
        int warmupIterations,
        int measuredIterations,
        String filename,
        BenchmarkGenerator generator
    ) throws IOException {
        if (warmupIterations < 0) {
            throw new IllegalArgumentException("warmupIterations must be non-negative");
        }
        if (measuredIterations <= 0) {
            throw new IllegalArgumentException("measuredIterations must be positive");
        }

        for (int i = 0; i < warmupIterations; i++) {
            generator.generate();
        }

        long[] elapsedNanos = new long[measuredIterations];
        for (int i = 0; i < measuredIterations; i++) {
            long startNanos = System.nanoTime();
            generator.generate();
            elapsedNanos[i] = System.nanoTime() - startNanos;
        }

        long totalNanos = 0;
        long minNanos = Long.MAX_VALUE;
        long maxNanos = 0;
        for (long elapsed : elapsedNanos) {
            totalNanos += elapsed;
            minNanos = Math.min(minNanos, elapsed);
            maxNanos = Math.max(maxNanos, elapsed);
        }

        Arrays.sort(elapsedNanos);
        double medianNanos;
        int middle = measuredIterations / 2;
        if ((measuredIterations & 1) == 0) {
            medianNanos = (elapsedNanos[middle - 1] + elapsedNanos[middle]) / 2.0;
        } else {
            medianNanos = elapsedNanos[middle];
        }

        double averageMillis = totalNanos / 1_000_000.0 / measuredIterations;
        double minMillis = minNanos / 1_000_000.0;
        double medianMillis = medianNanos / 1_000_000.0;
        double maxMillis = maxNanos / 1_000_000.0;
        System.out.printf(
            "%s benchmark: warmup=%d, iterations=%d, average=%.1f ms, median=%.1f ms, min=%.1f ms, max=%.1f ms%n",
            label, warmupIterations, measuredIterations, averageMillis, medianMillis, minMillis, maxMillis);

        GeneratedObject generated = generator.generate();
        Path output = storeBenchmarkGif(filename, generated);
        System.out.println(label + " benchmark image: " + output.toAbsolutePath());
    }

    public static Path storeBenchmarkGif(String filename, GeneratedObject generated) throws IOException {
        Path benchmarkDir = Files.createDirectories(Path.of("target", "benchmarks"));
        Path output = benchmarkDir.resolve(filename);
        Files.write(output, generated.getGifData());
        return output;
    }
}
