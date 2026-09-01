package com.joeking.northstarbridge;

import com.lightning.northstar.accessor.NorthstarLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

/**
 * Rebinds the active client level after Northstar rebuilds its client-side tracker.
 *
 * <p>During the initial single-player login, the registry sync can arrive before
 * {@link Minecraft#level} is assigned. Northstar's immediate rebind consequently has
 * no level to update; defer ours by a client tick so both objects are available.</p>
 */
@EventBusSubscriber(modid = NorthstarBridge.MOD_ID, value = Dist.CLIENT)
public final class ClientBridgeEvents {
    private static boolean rebindPending;
    private static int rebindDelayTicks;

    private ClientBridgeEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onClientRegistrySync(TagsUpdatedEvent event) {
        if (event.getUpdateCause() != TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED) {
            return;
        }

        requestRebind();
    }

    @SubscribeEvent
    public static void onClientLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel) {
            requestRebind();
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!rebindPending) {
            return;
        }

        if (rebindDelayTicks > 0) {
            rebindDelayTicks--;
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            // The registry can finish synchronizing before the initial client level exists.
            rebindDelayTicks = 1;
            return;
        }

        ((NorthstarLevel) level).northstar$onResourceReload();
        rebindPending = false;
    }

    private static void requestRebind() {
        rebindPending = true;
        // Always wait at least one completed client tick after the triggering event.
        rebindDelayTicks = Math.max(rebindDelayTicks, 1);
    }
}
