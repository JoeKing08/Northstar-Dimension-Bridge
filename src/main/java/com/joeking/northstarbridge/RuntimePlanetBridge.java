package com.joeking.northstarbridge;

import com.joeking.northstarbridge.mixin.PlanetConstructorInvoker;
import com.joeking.northstarbridge.mixin.PlanetTrackerAccessor;
import com.lightning.northstar.accessor.NorthstarLevel;
import com.lightning.northstar.content.NorthstarRegistries;
import com.lightning.northstar.planet.GravitationalSystem;
import com.lightning.northstar.planet.Planet;
import com.lightning.northstar.planet.PlanetTracker;
import com.lightning.northstar.planet.data.PlanetDimension;
import com.lightning.northstar.planet.data.PlanetProperties;
import com.lightning.northstar.planet.data.orbit.SimpleOrbitProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

final class RuntimePlanetBridge {
    private static final ResourceLocation SOL_ID = ResourceLocation.fromNamespaceAndPath("northstar", "sol");
    private static final ResourceKey<PlanetProperties> SOL_KEY = ResourceKey.create(NorthstarRegistries.PLANET, SOL_ID);
    private static final ResourceLocation MARS_SKY = ResourceLocation.fromNamespaceAndPath("northstar", "solar_system/mars_sky");

    private RuntimePlanetBridge() {
    }

    static int installServer(Collection<ResourceLocation> dimensions) {
        return install(NorthstarLevel.SERVER_TRACKER, dimensions);
    }

    static boolean installClient(Collection<ResourceLocation> dimensions) {
        return dimensions.isEmpty() || install(NorthstarLevel.CLIENT_TRACKER, dimensions) >= 0;
    }

    static void rebindServerLevels(MinecraftServer server, Collection<ResourceLocation> dimensions) {
        for (ServerLevel level : server.getAllLevels()) {
            if (dimensions.contains(level.dimension().location())) {
                ((NorthstarLevel) level).northstar$onResourceReload();
            }
        }
    }

    private static int install(PlanetTracker tracker, Collection<ResourceLocation> dimensions) {
        Planet sol = tracker.getPlanetById(SOL_ID);
        if (sol == null) {
            return -1;
        }

        PlanetTrackerAccessor accessor = (PlanetTrackerAccessor) (Object) tracker;
        List<Planet> added = new ArrayList<>();
        List<ResourceLocation> sorted = dimensions.stream().sorted(Comparator.comparing(ResourceLocation::toString)).toList();
        for (ResourceLocation dimension : sorted) {
            if (tracker.getPlanetByLevel(dimension) != null) {
                continue;
            }

            ResourceLocation planetId = ResourceLocation.fromNamespaceAndPath(
                    NorthstarBridge.MOD_ID, DimensionBridgeScanner.generatedName(dimension)
            );
            Planet planet = tracker.getPlanetById(planetId);
            PlanetDimension planetDimension;
            if (planet == null) {
                planetDimension = createDimension(planetId, dimension);
                planet = PlanetConstructorInvoker.northstarbridge$create(
                        ResourceKey.create(NorthstarRegistries.PLANET, planetId),
                        createProperties(dimension),
                        sol,
                        List.of(planetDimension)
                );
                tracker.getPlanets().put(planetId, planet);
                sol.satellites.add(planet);
                added.add(planet);
            } else {
                planetDimension = planet.dimensions.stream()
                        .filter(candidate -> candidate.dimensionId().location().equals(dimension))
                        .findFirst()
                        .orElseGet(() -> createDimension(planetId, dimension));
            }

            accessor.northstarbridge$levelToPlanet().put(dimension, planet);
            accessor.northstarbridge$levelToDimension().put(dimension, planetDimension);
        }

        if (added.isEmpty()) {
            return 0;
        }

        sol.satellites.sort(Comparator.comparingDouble(planet -> planet.properties.orbit().approximateRadius()));
        GravitationalSystem system = sol.system;
        for (Planet planet : added) {
            if (system != null) {
                planet.system = system;
                if (!system.planets().contains(planet)) {
                    system.planets().add(planet);
                }
                tracker.getSystems().put(planet.key.location(), system);
            }
            if (!accessor.northstarbridge$updateOrder().contains(planet)) {
                accessor.northstarbridge$updateOrder().add(planet);
            }
        }
        tracker.updateOrbits(tracker.getDeltaDays());
        return added.size();
    }

    private static PlanetProperties createProperties(ResourceLocation dimension) {
        return PlanetProperties.builder()
                .centralBody(SOL_KEY)
                .orbit(SimpleOrbitProvider.create(365.0, orbitRadius(dimension), 0.0, 0.0, 0.0))
                .type("planet")
                .requiredScience(0.0f)
                .canBeObserved(true)
                .rotationPeriodDays(1.0)
                .diameter(12000.0)
                .renderer(MARS_SKY)
                .notes(Component.literal(dimension.toString()))
                .build();
    }

    private static PlanetDimension createDimension(ResourceLocation planetId, ResourceLocation dimension) {
        return PlanetDimension.builder()
                .planet(ResourceKey.create(NorthstarRegistries.PLANET, planetId))
                .name("surface")
                .dimension(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension))
                .orbit(false)
                .gravity(9.807f)
                .temperature(15.0f)
                .build();
    }

    private static double orbitRadius(ResourceLocation dimension) {
        String hash = sha256(dimension.toString());
        return 1.5 + (Integer.parseUnsignedInt(hash.substring(0, 8), 16) % 8000) / 10000.0;
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
