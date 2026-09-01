package com.joeking.northstarbridge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record BridgeSnapshotPayload(List<ResourceLocation> dimensions) implements CustomPacketPayload {
    private static final int MAX_DIMENSIONS = 4096;

    public static final Type<BridgeSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(NorthstarBridge.MOD_ID, "dimension_snapshot")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, BridgeSnapshotPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BridgeSnapshotPayload decode(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > MAX_DIMENSIONS) {
                throw new DecoderException("Northstar bridge snapshot has invalid dimension count: " + count);
            }

            List<ResourceLocation> dimensions = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                dimensions.add(buffer.readResourceLocation());
            }
            return new BridgeSnapshotPayload(dimensions);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, BridgeSnapshotPayload payload) {
            buffer.writeVarInt(payload.dimensions.size());
            for (ResourceLocation dimension : payload.dimensions) {
                buffer.writeResourceLocation(dimension);
            }
        }
    };

    public BridgeSnapshotPayload(Collection<ResourceLocation> dimensions) {
        this(List.copyOf(dimensions));
        if (this.dimensions.size() > MAX_DIMENSIONS) {
            throw new IllegalArgumentException("Northstar bridge snapshot exceeds " + MAX_DIMENSIONS + " dimensions");
        }
    }

    @Override
    public Type<BridgeSnapshotPayload> type() {
        return TYPE;
    }
}
