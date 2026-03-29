package com.xinian.KryptonHybrid.tool;

import com.github.luben.zstd.Zstd;
import com.xinian.KryptonHybrid.shared.network.compression.ZstdDictionaryMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small CLI utility to pre-train a Zstd dictionary from packet/sample blobs.
 *
 * <p>Usage:
 * <pre>
 *   java com.xinian.KryptonHybrid.tool.ZstdDictionaryTrainer <samplesDir> <outputDictPath> [dictSize] [maxSamples]
 * </pre>
 *
 * <p>Output dictionaries are automatically wrapped with metadata including:
 * version, SHA-256 integrity hash, creation timestamp, and sample count.
 */
public final class ZstdDictionaryTrainer {

    private static final int DEFAULT_DICT_SIZE = 64 * 1024;
    private static final int DEFAULT_MAX_SAMPLES = 8000;
    private static final int MAX_SAMPLE_BYTES = 2 * 1024 * 1024;
    private static final int MIN_SAMPLES = 8;

    private ZstdDictionaryTrainer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 4) {
            printUsageAndExit();
            return;
        }

        Path samplesDir = Paths.get(args[0]).toAbsolutePath().normalize();
        Path outputPath = Paths.get(args[1]).toAbsolutePath().normalize();
        int dictSize = args.length >= 3 ? Integer.parseInt(args[2]) : DEFAULT_DICT_SIZE;
        int maxSamples = args.length >= 4 ? Integer.parseInt(args[3]) : DEFAULT_MAX_SAMPLES;

        if (!Files.isDirectory(samplesDir)) {
            throw new IllegalArgumentException("Sample directory not found: " + samplesDir);
        }
        if (dictSize < 1024) {
            throw new IllegalArgumentException("Dictionary size too small: " + dictSize);
        }
        if (maxSamples < MIN_SAMPLES) {
            throw new IllegalArgumentException("maxSamples too small: " + maxSamples);
        }

        System.out.println("Loading samples from: " + samplesDir);
        System.out.println("Max samples: " + maxSamples + ", Target dict size: " + dictSize + " bytes");

        List<byte[]> samples = loadSamples(samplesDir, maxSamples);
        if (samples.size() < MIN_SAMPLES) {
            throw new IllegalStateException("Need at least " + MIN_SAMPLES + " non-empty samples, found " + samples.size());
        }

        System.out.println("Training dictionary from " + samples.size() + " samples...");

        byte[] dictBuffer = new byte[dictSize];
        long result = Zstd.trainFromBuffer(samples.toArray(new byte[0][]), dictBuffer);
        if (Zstd.isError(result)) {
            throw new IllegalStateException("Dictionary training failed: " + Zstd.getErrorName(result));
        }

        int producedSize = (int) result;
        byte[] plainDict = new byte[producedSize];
        System.arraycopy(dictBuffer, 0, plainDict, 0, producedSize);

        // Get dictionary ID from trained dictionary
        long dictId = Zstd.getDictIdFromDict(plainDict);

        // Wrap with metadata
        byte[] wrappedDict;
        try {
            wrappedDict = ZstdDictionaryMetadata.wrap(plainDict, dictId, samples.size());
        } catch (IOException e) {
            System.err.println("Warning: Failed to wrap dictionary with metadata: " + e.getMessage());
            System.err.println("Saving as plain dictionary instead.");
            wrappedDict = plainDict;
        }

        // Write to file
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, wrappedDict);

        // Print results
        System.out.println();
        System.out.println("=== Dictionary Training Complete ===");
        System.out.println("Output file:     " + outputPath);
        System.out.println("Samples used:    " + samples.size());
        System.out.println("Plain dict size: " + producedSize + " bytes");
        System.out.println("Total file size: " + wrappedDict.length + " bytes");
        System.out.println("Dictionary ID:   " + dictId);
        System.out.println("Metadata header: " + (wrappedDict.length - producedSize) + " bytes");
        System.out.println();

        // Calculate and display compression stats
        displayCompressionStats(samples, plainDict);
    }

    private static List<byte[]> loadSamples(Path dir, int maxSamples) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.walk(dir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        System.out.println("Found " + files.size() + " files in directory");

        List<byte[]> result = new ArrayList<>(Math.min(maxSamples, files.size()));
        long totalBytes = 0;

        for (Path path : files) {
            if (result.size() >= maxSamples) {
                break;
            }

            long size = Files.size(path);
            if (size <= 0 || size > MAX_SAMPLE_BYTES) {
                continue;
            }

            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length > 0) {
                result.add(bytes);
                totalBytes += bytes.length;
            }
        }

        System.out.println("Loaded " + result.size() + " samples (" + formatBytes(totalBytes) + " total)");
        return result;
    }

    private static void displayCompressionStats(List<byte[]> samples, byte[] dict) {
        // Test compression ratio with dictionary (simplified estimation)
        long totalOriginal = samples.stream().mapToLong(s -> s.length).sum();

        try {
            // Create a test context with the dictionary
            com.github.luben.zstd.ZstdCompressCtx ctx = new com.github.luben.zstd.ZstdCompressCtx();
            ctx.setLevel(3);
            ctx.loadDict(dict);

            // Compress a sample to get ratio estimate
            if (!samples.isEmpty()) {
                byte[] testSample = samples.get(0);
                byte[] compressed = new byte[(int) com.github.luben.zstd.Zstd.compressBound(testSample.length)];
                long compressedSize = ctx.compress(compressed, testSample);

                if (!com.github.luben.zstd.Zstd.isError(compressedSize)) {
                    double ratio = (compressedSize * 100.0) / testSample.length;
                    System.out.println("Sample compression test (first sample, " + formatBytes(testSample.length) + "):");
                    System.out.println("  Original:  " + testSample.length + " bytes");
                    System.out.println("  Compressed: " + compressedSize + " bytes");
                    System.out.println("  Ratio:     " + String.format("%.2f%%", ratio));
                }
                ctx.close();
            }
        } catch (Exception e) {
            System.out.println("Note: Could not compute compression stats: " + e.getMessage());
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB"};
        int digitGroups = (int) (Math.log10(bytes) / Math.log10(1024));
        return String.format("%.2f %s", bytes / Math.pow(1024, digitGroups), units[digitGroups]);
    }

    private static void printUsageAndExit() {
        System.out.println("Usage:");
        System.out.println("  java com.xinian.KryptonHybrid.tool.ZstdDictionaryTrainer <samplesDir> <outputDictPath> [dictSize] [maxSamples]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  samplesDir      - Directory containing sample packet files");
        System.out.println("  outputDictPath  - Path where trained dictionary will be saved");
        System.out.println("  dictSize        - Target dictionary size in bytes (default: 65536)");
        System.out.println("  maxSamples      - Maximum number of samples to use (default: 8000)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java com.xinian.KryptonHybrid.tool.ZstdDictionaryTrainer run/krypton_samples config/krypton_hybrid.zdict 65536 8000");
    }
}

