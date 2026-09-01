package com.joeking.northstarbridge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(NorthstarBridge.MOD_ID)
public final class NorthstarBridge {
    public static final String MOD_ID = "northstarbridge";

    public NorthstarBridge(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, BridgeConfig.SPEC);
        modBus.addListener(NorthstarBridge::registerPayloadHandlers);
    }

    private static void registerPayloadHandlers(RegisterPayloadHandlersEvent event) {
        BridgeNetworking.register(event);
    }
}
