package com.xinian.KryptonHybrid;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.xinian.KryptonHybrid.shared.KryptonConfig;
import com.xinian.KryptonHybrid.shared.KryptonSharedBootstrap;
import com.xinian.KryptonHybrid.shared.ProxyMode;
import com.xinian.KryptonHybrid.shared.network.compression.CompressionAlgorithm;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Fabric-specific configuration for Krypton Hybrid.
 *
 * <p>Reads and writes a JSON config file at {@code config/krypton_hybrid.json}.
 * Values are loaded into {@link KryptonConfig} on startup.</p>
 */
public final class KryptonFabricConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "krypton_hybrid.json";

    private KryptonFabricConfig() {}

    /**
     * Loads config from disk into {@link KryptonConfig}. Creates a default config file
     * if one does not exist.
     */
    public static void load() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        Path configFile = configDir.resolve(CONFIG_FILE);

        JsonObject root;
        if (Files.exists(configFile)) {
            try (Reader reader = Files.newBufferedReader(configFile)) {
                root = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                KryptonSharedBootstrap.LOGGER.warn("Failed to read Krypton Hybrid config, using defaults", e);
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }

        // compression
        JsonObject compression = getOrCreate(root, "compression");
        KryptonConfig.compressionAlgorithm = readEnum(compression, "algorithm",
                CompressionAlgorithm.class, CompressionAlgorithm.ZSTD);
        KryptonConfig.zstdLevel = readInt(compression, "zstd_level", 3);

        // zstd_advanced
        JsonObject zstdAdv = getOrCreate(root, "zstd_advanced");
        KryptonConfig.zstdWorkers               = readInt(zstdAdv, "workers", 0);
        KryptonConfig.zstdOverlapLog            = readInt(zstdAdv, "overlap_log", 0);
        KryptonConfig.zstdJobSize               = readInt(zstdAdv, "job_size", 0);
        KryptonConfig.zstdEnableLDM             = readBool(zstdAdv, "enable_long_distance_matching", false);
        KryptonConfig.zstdLongDistanceWindowLog = readInt(zstdAdv, "long_distance_window_log", 27);
        KryptonConfig.zstdStrategy              = readInt(zstdAdv, "strategy", 0);
        KryptonConfig.zstdDictEnabled           = readBool(zstdAdv, "dict_enabled", false);
        KryptonConfig.zstdDictPath              = readString(zstdAdv, "dict_path", "config/krypton_hybrid.zdict");
        KryptonConfig.zstdDictRequired          = readBool(zstdAdv, "dict_required", false);

        // light_opt
        JsonObject lightOpt = getOrCreate(root, "light_opt");
        KryptonConfig.lightOptEnabled = readBool(lightOpt, "enabled", true);

        // chunk_data_opt
        JsonObject chunkOpt = getOrCreate(root, "chunk_data_opt");
        KryptonConfig.chunkOptEnabled = readBool(chunkOpt, "enabled", true);

        // dcc
        JsonObject dcc = getOrCreate(root, "dcc");
        KryptonConfig.dccEnabled        = readBool(dcc, "enabled", true);
        KryptonConfig.dccSizeLimit      = readInt(dcc, "size_limit", 60);
        KryptonConfig.dccDistance       = readInt(dcc, "distance", 5);
        KryptonConfig.dccTimeoutSeconds = readInt(dcc, "timeout_seconds", 30);

        // broadcast_cache
        JsonObject broadcastCache = getOrCreate(root, "broadcast_cache");
        KryptonConfig.broadcastCacheEnabled = readBool(broadcastCache, "enabled", true);

        // packet_coalescing
        JsonObject coalescing = getOrCreate(root, "packet_coalescing");
        KryptonConfig.packetCoalescingEnabled = readBool(coalescing, "enabled", true);

        // block_entity_delta
        JsonObject beDelta = getOrCreate(root, "block_entity_delta");
        KryptonConfig.blockEntityDeltaEnabled = readBool(beDelta, "enabled", true);

        // security
        JsonObject security = getOrCreate(root, "security");
        KryptonConfig.securityEnabled                = readBool(security, "enabled", true);
        KryptonConfig.securityPacketRateLimitEnabled = readBool(security, "packet_rate_limit_enabled", true);

        // proxy
        JsonObject proxy = getOrCreate(root, "proxy");
        KryptonConfig.proxyMode                = readEnum(proxy, "mode", ProxyMode.class, ProxyMode.NONE);
        KryptonConfig.velocityForwardingSecret = readString(proxy, "forwarding_secret", "");

        // Write back with all defaults filled in
        try {
            Files.createDirectories(configDir);
            try (Writer writer = Files.newBufferedWriter(configFile)) {
                GSON.toJson(buildJson(), writer);
            }
        } catch (IOException e) {
            KryptonSharedBootstrap.LOGGER.warn("Failed to write Krypton Hybrid config", e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static JsonObject getOrCreate(JsonObject parent, String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        JsonObject obj = new JsonObject();
        parent.add(key, obj);
        return obj;
    }

    private static int readInt(JsonObject obj, String key, int def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        try { return el.getAsInt(); } catch (Exception e) { return def; }
    }

    private static boolean readBool(JsonObject obj, String key, boolean def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        try { return el.getAsBoolean(); } catch (Exception e) { return def; }
    }

    private static String readString(JsonObject obj, String key, String def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        return el.getAsString();
    }

    private static <E extends Enum<E>> E readEnum(JsonObject obj, String key, Class<E> type, E def) {
        JsonElement el = obj.get(key);
        if (el == null || !el.isJsonPrimitive()) return def;
        try { return Enum.valueOf(type, el.getAsString().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return def; }
    }

    /** Builds a JSON object reflecting the currently-active config values. */
    private static JsonObject buildJson() {
        JsonObject root = new JsonObject();

        JsonObject compression = new JsonObject();
        compression.addProperty("algorithm", KryptonConfig.compressionAlgorithm.name());
        compression.addProperty("zstd_level", KryptonConfig.zstdLevel);
        root.add("compression", compression);

        JsonObject zstdAdv = new JsonObject();
        zstdAdv.addProperty("workers", KryptonConfig.zstdWorkers);
        zstdAdv.addProperty("overlap_log", KryptonConfig.zstdOverlapLog);
        zstdAdv.addProperty("job_size", KryptonConfig.zstdJobSize);
        zstdAdv.addProperty("enable_long_distance_matching", KryptonConfig.zstdEnableLDM);
        zstdAdv.addProperty("long_distance_window_log", KryptonConfig.zstdLongDistanceWindowLog);
        zstdAdv.addProperty("strategy", KryptonConfig.zstdStrategy);
        zstdAdv.addProperty("dict_enabled", KryptonConfig.zstdDictEnabled);
        zstdAdv.addProperty("dict_path", KryptonConfig.zstdDictPath);
        zstdAdv.addProperty("dict_required", KryptonConfig.zstdDictRequired);
        root.add("zstd_advanced", zstdAdv);

        JsonObject lightOpt = new JsonObject();
        lightOpt.addProperty("enabled", KryptonConfig.lightOptEnabled);
        root.add("light_opt", lightOpt);

        JsonObject chunkOpt = new JsonObject();
        chunkOpt.addProperty("enabled", KryptonConfig.chunkOptEnabled);
        root.add("chunk_data_opt", chunkOpt);

        JsonObject dcc = new JsonObject();
        dcc.addProperty("enabled", KryptonConfig.dccEnabled);
        dcc.addProperty("size_limit", KryptonConfig.dccSizeLimit);
        dcc.addProperty("distance", KryptonConfig.dccDistance);
        dcc.addProperty("timeout_seconds", KryptonConfig.dccTimeoutSeconds);
        root.add("dcc", dcc);

        JsonObject broadcastCache = new JsonObject();
        broadcastCache.addProperty("enabled", KryptonConfig.broadcastCacheEnabled);
        root.add("broadcast_cache", broadcastCache);

        JsonObject coalescing = new JsonObject();
        coalescing.addProperty("enabled", KryptonConfig.packetCoalescingEnabled);
        root.add("packet_coalescing", coalescing);

        JsonObject beDelta = new JsonObject();
        beDelta.addProperty("enabled", KryptonConfig.blockEntityDeltaEnabled);
        root.add("block_entity_delta", beDelta);

        JsonObject security = new JsonObject();
        security.addProperty("enabled", KryptonConfig.securityEnabled);
        security.addProperty("packet_rate_limit_enabled", KryptonConfig.securityPacketRateLimitEnabled);
        root.add("security", security);

        JsonObject proxy = new JsonObject();
        proxy.addProperty("mode", KryptonConfig.proxyMode.name());
        proxy.addProperty("forwarding_secret", KryptonConfig.velocityForwardingSecret);
        root.add("proxy", proxy);

        return root;
    }
}
