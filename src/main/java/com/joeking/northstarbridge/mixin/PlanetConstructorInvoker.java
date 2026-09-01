package com.joeking.northstarbridge.mixin;

import com.lightning.northstar.planet.Planet;
import com.lightning.northstar.planet.data.PlanetDimension;
import com.lightning.northstar.planet.data.PlanetProperties;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(Planet.class)
public interface PlanetConstructorInvoker {
    @Invoker("<init>")
    static Planet northstarbridge$create(
            ResourceKey<PlanetProperties> key,
            PlanetProperties properties,
            Planet centralBody,
            List<PlanetDimension> dimensions
    ) {
        throw new AssertionError();
    }
}
