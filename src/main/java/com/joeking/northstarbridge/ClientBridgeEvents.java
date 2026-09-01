package com.joeking.northstarbridge;

import com.lightning.northstar.accessor.NorthstarLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.TagsUpdatedEvent;

/** Rebinds the active client level after Northstar rebuilds its client-side tracker. */
@EventBusSubscriber(modid = NorthstarBridge.MOD_ID, value = Dist.CLIENT)
public final class ClientBridgeEvents {
    private ClientBridgeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientRegistrySync(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            ((NorthstarLevel) level).northstar$onResourceReload();
        }
    }
}
