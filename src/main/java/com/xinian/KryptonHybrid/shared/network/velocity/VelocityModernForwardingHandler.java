package com.xinian.KryptonHybrid.shared.network.velocity;

import com.mojang.authlib.GameProfile;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles Velocity Modern Forwarding protocol logic.
 *
 * <p>Manages pending login plugin queries sent to the proxy and processes
 * the HMAC-signed forwarding data returned by Velocity.</p>
 */
public final class VelocityModernForwardingHandler {

    private static final AtomicInteger txCounter = new AtomicInteger(0);
    private static final Map<Integer, Boolean> pendingQueries = new ConcurrentHashMap<>();

    private VelocityModernForwardingHandler() {}

    /** Generates a unique transaction ID for a new forwarding query. */
    public static int generateTransactionId() {
        return txCounter.incrementAndGet();
    }

    /** Registers a transaction ID as a pending forwarding query. */
    public static void registerPendingQuery(int txId) {
        pendingQueries.put(txId, Boolean.TRUE);
    }

    /** Returns {@code true} if the given transaction ID is a pending forwarding query. */
    public static boolean isPendingQuery(int txId) {
        return pendingQueries.containsKey(txId);
    }

    /** Removes a pending forwarding query by transaction ID. */
    public static void removePendingQuery(int txId) {
        pendingQueries.remove(txId);
    }

    /**
     * Processes Velocity modern forwarding data, verifying the HMAC signature
     * and extracting player information.
     *
     * <p><strong>Note:</strong> This is a stub implementation. Full HMAC-SHA256
     * verification and Velocity forwarding data parsing are not yet implemented.
     * Modern forwarding will not function until this method is completed.</p>
     *
     * @param data   raw forwarding data bytes from the proxy
     * @param secret shared forwarding secret
     * @return a {@link ForwardingResult} with the player profile and address, or
     *         {@code null} if verification fails (currently always {@code null})
     */
    public static ForwardingResult processForwardingData(byte[] data, String secret) {
        // Stub: HMAC-SHA256 verification and Velocity forwarding data parsing
        // are not yet implemented. Always returns null (modern forwarding disabled).
        return null;
    }

    /** Holds the result of a successful Velocity modern forwarding handshake. */
    public record ForwardingResult(GameProfile profile, SocketAddress remoteAddress) {}
}
