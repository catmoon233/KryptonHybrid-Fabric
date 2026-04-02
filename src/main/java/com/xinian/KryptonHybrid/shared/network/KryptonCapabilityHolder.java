package com.xinian.KryptonHybrid.shared.network;

import net.minecraft.network.Connection;

import java.net.SocketAddress;

/**
 * Interface mixed into {@link Connection} to provide Krypton capability state
 * and address override support.
 */
public interface KryptonCapabilityHolder {

    /** Overrides the remote address reported by this connection. */
    void krypton$setAddress(SocketAddress address);

    /** Returns the Krypton capability flags for this connection. */
    KryptonCapabilities krypton$getCapabilities();

    /**
     * Capability flags for a single Krypton connection.
     * Tracks whether the connection is behind a proxy and which custom
     * wire formats have been negotiated.
     */
    class KryptonCapabilities {

        private volatile boolean behindProxy = false;

        public boolean isBehindProxy() {
            return behindProxy;
        }

        public void setBehindProxy(boolean behindProxy) {
            this.behindProxy = behindProxy;
        }
    }
}
