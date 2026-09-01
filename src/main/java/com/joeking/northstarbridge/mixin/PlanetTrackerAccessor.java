package com.joeking.northstarbridge.mixin;

import com.lightning.northstar.planet.Planet;
import com.lightning.northstar.planet.PlanetTracker;
import com.lightning.northstar.planet.data.PlanetDimension;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

@Mixin(PlanetTracker.class)
public interface PlanetTrackerAccessor {
    @Accessor("levelToPlanet")
    Map<ResourceLocation, Planet> northstarbridge$levelToPlanet();

    @Accessor("levelToDimension")
    Map<ResourceLocation, PlanetDimension> northstarbridge$levelToDimension();

    @Accessor("updateOrder")
    List<Planet> northstarbridge$updateOrder();
}
