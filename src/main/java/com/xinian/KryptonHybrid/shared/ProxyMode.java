package com.xinian.KryptonHybrid.shared;

/**
 * Controls how Krypton Hybrid interacts with reverse proxies (e.g. Velocity).
 */
public enum ProxyMode {
    /** No proxy; all optimizations active (direct connection). */
    NONE,
    /**
     * Auto-detect Velocity via login plugin channel.
     * When detected, forces ZLIB on backend and gates custom wire formats
     * behind capability negotiation.
     */
    AUTO,
    /**
     * Assume Velocity proxy; always use ZLIB backend compression and gate
     * custom wire formats.
     */
    VELOCITY
}
