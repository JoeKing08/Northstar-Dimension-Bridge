package com.joeking.northstarbridge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Collection;

final class BridgeNetworking {
    private BridgeNetworking() {
    }

    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(BridgeSnapshotPayload.TYPE, BridgeSnapshotPayload.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(
                                () -> ClientBridgeEvents.applyRuntimeSnapshot(payload.dimensions())
                        ));
    }

    static void sendSnapshot(ServerPlayer player, Collection<ResourceLocation> dimensions) {
        PacketDistributor.sendToPlayer(player, new BridgeSnapshotPayload(dimensions));
    }

    static void broadcastSnapshot(Collection<ResourceLocation> dimensions) {
        PacketDistributor.sendToAllPlayers(new BridgeSnapshotPayload(dimensions));
    }
}
