package com.xinian.KryptonHybrid.shared.network.velocity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Login plugin payload sent by Velocity to forward player info to the backend server.
 * Parsed from the raw bytes of a {@code ServerboundCustomQueryAnswerPacket}.
 */
public record VelocityForwardingPayload(byte[] data) implements CustomQueryAnswerPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("velocity", "player_info");

    public static final StreamCodec<FriendlyByteBuf, VelocityForwardingPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBytes(payload.data()),
                    buf -> {
                        byte[] bytes = new byte[buf.readableBytes()];
                        buf.readBytes(bytes);
                        return new VelocityForwardingPayload(bytes);
                    }
            );

    @Override
    public ResourceLocation id() {
        return ID;
    }
}
