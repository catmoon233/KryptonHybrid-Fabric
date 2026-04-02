package com.xinian.KryptonHybrid.shared.network.velocity;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Login plugin message sent to the client (via Velocity proxy) to request
 * modern player-info forwarding data.
 */
public record VelocityLoginQueryPayload(int forwardingVersion) implements CustomQueryPayload {

    /** Forwarding version: MODERN_LAZY_SESSION (supports 1.19.3+ GameProfile signing). */
    public static final int MODERN_LAZY_SESSION = 3;

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("velocity", "player_info");

    public static final StreamCodec<FriendlyByteBuf, VelocityLoginQueryPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeByte(payload.forwardingVersion()),
                    buf -> new VelocityLoginQueryPayload(buf.readUnsignedByte())
            );

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
