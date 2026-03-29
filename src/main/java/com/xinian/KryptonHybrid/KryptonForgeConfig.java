package com.xinian.KryptonHybrid;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.network.compression.CompressionAlgorithm;
import org.apache.commons.lang3.tuple.Pair;

/**
 * NeoForge-specific configuration definition for Krypton Hybrid (1.21.1).
 *
 * <p>The config file is registered as type {@link net.neoforged.fml.config.ModConfig.Type#COMMON COMMON},
 * which means NeoForge generates it immediately at mod load time under
 * {@code config/krypton_hybrid-common.toml} on both clients and dedicated servers.
 * The file is created on the first launch and can be edited while the game is not running.</p>
 *
 * <p>Values are pushed into {@link KryptonConfig} via {@link #bake()} whenever the config
 * file is loaded or reloaded.</p>
 */
public final class KryptonForgeConfig {

    /** The built {@link ModConfigSpec}. Pass this to {@code registerConfig()}. */
    public static final ModConfigSpec SPEC;

    /** The singleton config instance. */
    public static final KryptonForgeConfig INSTANCE;

    static {
        Pair<KryptonForgeConfig, ModConfigSpec> pair =
                new ModConfigSpec.Builder().configure(KryptonForgeConfig::new);
        INSTANCE = pair.getLeft();
        SPEC     = pair.getRight();
    }

    private final ModConfigSpec.EnumValue<CompressionAlgorithm> algorithm;
    private final ModConfigSpec.IntValue                        zstdLevel;
    private final ModConfigSpec.IntValue                        zstdWorkers;
    private final ModConfigSpec.IntValue                        zstdOverlapLog;
    private final ModConfigSpec.IntValue                        zstdJobSize;
    private final ModConfigSpec.BooleanValue                    zstdEnableLDM;
    private final ModConfigSpec.IntValue                        zstdLongDistanceWindowLog;
    private final ModConfigSpec.IntValue                        zstdStrategy;
    private final ModConfigSpec.BooleanValue                    lightOptEnabled;
    private final ModConfigSpec.BooleanValue                    dccEnabled;
    private final ModConfigSpec.IntValue                        dccSizeLimit;
    private final ModConfigSpec.IntValue                        dccDistance;
    private final ModConfigSpec.IntValue                        dccTimeoutSeconds;

    private KryptonForgeConfig(ModConfigSpec.Builder builder) {
        builder.comment(
                "Krypton Hybrid - Packet Compression",
                "Select the compression algorithm used for all player connections.",
                "IMPORTANT: BOTH the server and every connecting client must use the same",
                "algorithm. Mismatched algorithms will immediately corrupt the session."
        ).push("compression");

        algorithm = builder
                .comment(
                        "Compression algorithm to use for network packets.",
                        "  ZLIB  - vanilla zlib (DEFLATE). Always available. Minecraft default.",
                        "  ZSTD  - Zstandard compression (zstd-jni, native). Best ratio; moderate CPU.",
                        "Default: ZSTD"
                )
                .defineEnum("algorithm", CompressionAlgorithm.ZSTD);

        zstdLevel = builder
                .comment(
                        "Zstd compression level (1 = fastest/largest, 22 = slowest/smallest).",
                        "Only used when algorithm = ZSTD. Backed by zstd-jni (native).",
                        "Level 3 is the zstd reference implementation default.",
                        "Range: 1 \u2013 22  |  Default: 3"
                )
                .defineInRange("zstd_level", 3, 1, 22);

        builder.pop();

        builder.comment(
                "Krypton Hybrid - Zstd Advanced / Parallel Compression",
                "Fine-grained control over Zstd's native multi-threaded compression",
                "and match-finding parameters.  These settings only take effect when",
                "compression.algorithm = ZSTD.  Changes require a server restart",
                "(new connections will use the updated values).",
                "",
                "WARNING: Misconfigured values can increase CPU usage or memory",
                "consumption significantly.  The defaults are safe for all scenarios."
        ).push("zstd_advanced");

        zstdWorkers = builder
                .comment(
                        "Number of native worker threads for parallel Zstd compression.",
                        "0 = single-threaded (compression runs in the Netty I/O thread).",
                        "Values >= 1 activate libzstd's multi-threaded mode: input data is",
                        "split into jobs compressed in parallel by a per-connection native",
                        "thread pool.  Useful when compressing large payloads (chunk data,",
                        "recipe sync) on multi-core CPUs.",
                        "",
                        "Total native threads = workers × active connections, so keep this",
                        "low on high-player-count servers.",
                        "Range: 0 \u2013 128  |  Default: 0 (single-threaded)"
                )
                .defineInRange("workers", 0, 0, 128);

        zstdOverlapLog = builder
                .comment(
                        "Overlap log for multi-threaded compression.",
                        "Controls how much context (dictionary data) each worker thread",
                        "shares with the previous thread's output.  Higher values improve",
                        "compression ratio but use more memory per job.",
                        "Only meaningful when workers >= 1.",
                        "overlap_size = 2^(overlapLog) KB.",
                        "0 = auto (Zstd picks a value based on compression level).",
                        "Range: 0 \u2013 9  |  Default: 0"
                )
                .defineInRange("overlap_log", 0, 0, 9);

        zstdJobSize = builder
                .comment(
                        "Job size (bytes) for multi-threaded compression.",
                        "Minimum input partition per worker thread.  Smaller values increase",
                        "parallelism for small payloads but add scheduling overhead.",
                        "Only meaningful when workers >= 1.",
                        "0 = auto (Zstd selects based on compression level and overlap).",
                        "Range: 0 (auto) or 512 \u2013 1073741824  |  Default: 0"
                )
                .defineInRange("job_size", 0, 0, 1073741824);

        zstdEnableLDM = builder
                .comment(
                        "Enable Zstd long-distance matching (LDM).",
                        "When true, Zstd searches for repeated byte sequences across a",
                        "much larger window than the standard match finder.  Improves ratio",
                        "for highly repetitive data (flat-world chunks, bulk NBT) at the",
                        "cost of higher memory usage.",
                        "Default: false"
                )
                .define("enable_long_distance_matching", false);

        zstdLongDistanceWindowLog = builder
                .comment(
                        "Window log for long-distance matching.",
                        "Sets the LDM window size exponent: window = 2^windowLog bytes.",
                        "Only used when enable_long_distance_matching = true.",
                        "20 = 1 MB, 24 = 16 MB, 27 = 128 MB (Zstd default).",
                        "For Minecraft traffic, 20\u201324 is usually sufficient.",
                        "Range: 10 \u2013 30  |  Default: 27"
                )
                .defineInRange("long_distance_window_log", 27, 10, 30);

        zstdStrategy = builder
                .comment(
                        "Zstd compression strategy (match-finding algorithm).",
                        "Higher strategies find better matches but use more CPU.",
                        "0 = auto (determined by compression level).",
                        "1 = fast,  2 = dfast,  3 = greedy,  4 = lazy,  5 = lazy2,",
                        "6 = btlazy2,  7 = btopt,  8 = btultra,  9 = btultra2.",
                        "Values above 5 are NOT recommended for real-time game servers.",
                        "Range: 0 \u2013 9  |  Default: 0 (auto)"
                )
                .defineInRange("strategy", 0, 0, 9);

        builder.pop();

        builder.comment(
                "Krypton Hybrid - Light Data Optimization",
                "Reduces ClientboundLevelChunkWithLightPacket size by replacing uniform",
                "DataLayer arrays (e.g. all-max sky-light sections) with 2-byte tokens.",
                "Requires Krypton Hybrid on BOTH the server and every connecting client."
        ).push("light_opt");

        lightOptEnabled = builder
                .comment(
                        "Enable uniform-RLE encoding for light DataLayer arrays.",
                        "Saves up to ~40 KB per chunk load in open-sky environments.",
                        "Default: true"
                )
                .define("enabled", true);

        builder.pop();

        builder.comment(
                "Delayed Chunk Cache (DCC)",
                "Reduces redundant chunk resends when a player moves near the edge of",
                "their view distance. Departing chunks are buffered; if the player",
                "re-enters range within the timeout, the full resend is skipped."
        ).push("dcc");

        dccEnabled = builder
                .comment(
                        "Enable or disable the Delayed Chunk Cache entirely.",
                        "Default: true"
                )
                .define("enabled", true);

        dccSizeLimit = builder
                .comment(
                        "Maximum number of chunks buffered per player.",
                        "When full, new departing chunks are untracked immediately.",
                        "Range: 1 \u2013 200  |  Default: 60"
                )
                .defineInRange("size_limit", 60, 1, 200);

        dccDistance = builder
                .comment(
                        "Cache radius (chunks) around the player's current position.",
                        "Cached chunks farther than this are evicted and untracked.",
                        "Range: 1 \u2013 32  |  Default: 5"
                )
                .defineInRange("distance", 5, 1, 32);

        dccTimeoutSeconds = builder
                .comment(
                        "Seconds before a cached chunk is forcibly evicted.",
                        "Higher values improve hit rate; lower values reduce client memory.",
                        "Range: 5 \u2013 300  |  Default: 30"
                )
                .defineInRange("timeout_seconds", 30, 5, 300);

        builder.pop();
    }

    /**
     * Copies the current config values into {@link KryptonConfig}.
     * Called from {@link kryptonhybrid} in response to
     * {@link ModConfigEvent.Loading} and {@link ModConfigEvent.Reloading}.
     */
    public void bake() {
        KryptonConfig.compressionAlgorithm = algorithm.get();
        KryptonConfig.zstdLevel            = zstdLevel.get();
        KryptonConfig.zstdWorkers          = zstdWorkers.get();
        KryptonConfig.zstdOverlapLog       = zstdOverlapLog.get();
        KryptonConfig.zstdJobSize          = zstdJobSize.get();
        KryptonConfig.zstdEnableLDM        = zstdEnableLDM.get();
        KryptonConfig.zstdLongDistanceWindowLog = zstdLongDistanceWindowLog.get();
        KryptonConfig.zstdStrategy         = zstdStrategy.get();
        KryptonConfig.lightOptEnabled      = lightOptEnabled.get();
        KryptonConfig.dccEnabled           = dccEnabled.get();
        KryptonConfig.dccSizeLimit         = dccSizeLimit.get();
        KryptonConfig.dccDistance          = dccDistance.get();
        KryptonConfig.dccTimeoutSeconds    = dccTimeoutSeconds.get();
    }
}

