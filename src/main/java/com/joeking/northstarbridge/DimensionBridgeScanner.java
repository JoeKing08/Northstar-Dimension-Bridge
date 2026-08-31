package com.joeking.northstarbridge;

import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelResource;
import com.lightning.northstar.content.NorthstarRegistries;
import com.lightning.northstar.planet.data.PlanetDimension;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid = NorthstarBridge.MOD_ID)
public final class DimensionBridgeScanner {
    private static final Logger LOGGER = LoggerFactory.getLogger(NorthstarBridge.MOD_ID);
    private static final String GENERATED_PACK = "northstarbridge_generated";
    private static final String DATA_NAMESPACE = NorthstarBridge.MOD_ID;
    private static final ResourceLocation OVERWORLD = ResourceLocation.withDefaultNamespace("overworld");
    private static final Set<ResourceLocation> RESERVED = Set.of(OVERWORLD);

    private static MinecraftServer server;
    private static Set<ResourceLocation> lastEligible = Set.of();
    private static boolean reloadPending;
    private static int tickCounter;

    private DimensionBridgeScanner() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        server = event.getServer();
        scan(true);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (server == null || reloadPending) {
            return;
        }
        tickCounter++;
        if (tickCounter >= BridgeConfig.SCAN_INTERVAL_TICKS.get()) {
            tickCounter = 0;
            scan(false);
        }
    }

    private static void scan(boolean initial) {
        Set<ResourceLocation> eligible = collectEligibleDimensions(server);
        if (!initial && eligible.equals(lastEligible)) {
            return;
        }

        try {
            writeGeneratedPack(server, eligible);
            lastEligible = Set.copyOf(eligible);
            reloadResources(server);
            LOGGER.info("Northstar bridge discovered {} eligible dimensions; reloading server data", eligible.size());
        } catch (IOException | RuntimeException exception) {
            LOGGER.error("Failed to update generated Northstar dimension data", exception);
        }
    }

    private static Set<ResourceLocation> collectEligibleDimensions(MinecraftServer minecraftServer) {
        Set<ResourceLocation> dimensions = new HashSet<>();
        for (ServerLevel level : minecraftServer.getAllLevels()) {
            dimensions.add(level.dimension().location());
        }
        minecraftServer.registryAccess().registry(Registries.LEVEL_STEM).ifPresent(registry ->
                registry.keySet().forEach(dimensions::add)
        );

        Set<ResourceLocation> alreadyMapped = externallyMappedDimensions(minecraftServer);
        dimensions.removeIf(dimension -> !isEligible(dimension) || alreadyMapped.contains(dimension));
        return dimensions;
    }

    private static Set<ResourceLocation> externallyMappedDimensions(MinecraftServer minecraftServer) {
        Set<ResourceLocation> mapped = new HashSet<>();
        minecraftServer.registryAccess().registry(NorthstarRegistries.PLANET_DIMENSION).ifPresent(registry ->
                registry.entrySet().forEach(entry -> {
                    ResourceLocation mappingId = entry.getKey().location();
                    PlanetDimension mapping = entry.getValue();
                    ResourceLocation planetId = mapping.planet() == null ? null : mapping.planet().location();
                    boolean ownedByBridge = mappingId.getNamespace().equals(DATA_NAMESPACE)
                            || (planetId != null && planetId.getNamespace().equals(DATA_NAMESPACE));
                    if (!ownedByBridge && mapping.dimensionId() != null) {
                        mapped.add(mapping.dimensionId().location());
                    }
                })
        );
        return mapped;
    }

    private static boolean isEligible(ResourceLocation dimension) {
        if (RESERVED.contains(dimension) || dimension.getNamespace().equals(NorthstarBridge.MOD_ID)
                || dimension.getNamespace().equals("northstar")) {
            return false;
        }

        Set<String> blacklist = new HashSet<>(BridgeConfig.BLACKLIST.get());
        if (blacklist.contains(dimension.toString())) {
            return false;
        }

        List<? extends String> whitelist = BridgeConfig.WHITELIST.get();
        return whitelist.isEmpty() || whitelist.contains(dimension.toString());
    }

    private static void writeGeneratedPack(MinecraftServer minecraftServer, Collection<ResourceLocation> dimensions) throws IOException {
        Path packRoot = minecraftServer.getWorldPath(LevelResource.ROOT).resolve("datapacks").resolve(GENERATED_PACK);
        Path dataRoot = packRoot.resolve("data").resolve(DATA_NAMESPACE).resolve("northstar");
        Path planetRoot = dataRoot.resolve("planet");
        Path dimensionRoot = dataRoot.resolve("planet_dimension");
        Files.createDirectories(planetRoot);
        Files.createDirectories(dimensionRoot);
        writeAtomically(packRoot.resolve("pack.mcmeta"), "{\n  \"pack\": {\n    \"pack_format\": 48,\n    \"description\": \"Northstar Dimension Bridge generated data\"\n  }\n}\n");

        Set<String> activeNames = new HashSet<>();
        List<ResourceLocation> sorted = new ArrayList<>(dimensions);
        sorted.sort(Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation dimension : sorted) {
            String name = generatedName(dimension);
            activeNames.add(name);
            ResourceLocation planet = ResourceLocation.fromNamespaceAndPath(DATA_NAMESPACE, name);
            writeAtomically(planetRoot.resolve(name + ".json"), planetJson(planet, dimension));
            writeAtomically(dimensionRoot.resolve(name + ".json"), planetDimensionJson(planet, dimension));
        }

        pruneGeneratedFiles(planetRoot, activeNames);
        pruneGeneratedFiles(dimensionRoot, activeNames);
    }

    private static void pruneGeneratedFiles(Path directory, Set<String> activeNames) throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (var files = Files.list(directory)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .filter(path -> !activeNames.contains(path.getFileName().toString().replaceFirst("\\.json$", "")))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new BridgeWriteException(exception);
                        }
                    });
        } catch (BridgeWriteException exception) {
            throw exception.exception;
        }
    }

    private static void reloadResources(MinecraftServer minecraftServer) {
        var repository = minecraftServer.getPackRepository();
        repository.reload();
        String generatedPackId = repository.getAvailableIds().stream()
                .filter(id -> id.equals(GENERATED_PACK)
                        || id.equals("file/" + GENERATED_PACK)
                        || id.endsWith("/" + GENERATED_PACK))
                .findFirst()
                .orElse(null);
        if (generatedPackId == null) {
            LOGGER.warn("Generated data pack {} was not discovered by the server", GENERATED_PACK);
            return;
        }

        List<String> selected = new ArrayList<>(repository.getSelectedIds());
        if (!selected.contains(generatedPackId)) {
            selected.add(generatedPackId);
        }
        CompletableFuture<Void> reload = minecraftServer.reloadResources(selected);
        reloadPending = true;
        reload.whenComplete((ignored, exception) -> {
            reloadPending = false;
            if (exception != null) {
                LOGGER.error("Northstar data reload failed", exception);
            }
        });
    }

    private static String generatedName(ResourceLocation dimension) {
        String hash = sha256(dimension.toString()).substring(0, 16);
        return "dimension_" + hash;
    }

    private static String planetJson(ResourceLocation planet, ResourceLocation dimension) {
        double radius = 1.5 + (Integer.parseUnsignedInt(sha256(dimension.toString()).substring(0, 8), 16) % 8000) / 10000.0;
        return "{\n"
                + "  \"central_body\": \"northstar:sol\",\n"
                + "  \"orbit\": {\n"
                + "    \"type\": \"northstar:simple\",\n"
                + "    \"duration_days\": 365.0,\n"
                + "    \"radius\": " + String.format(java.util.Locale.ROOT, "%.5f", radius) + "\n"
                + "  },\n"
                + "  \"class\": \"planet\",\n"
                + "  \"required_science\": 0.0,\n"
                + "  \"can_be_observed\": true,\n"
                + "  \"rotation_period_days\": 1.0,\n"
                + "  \"diameter\": 12000.0,\n"
                + "  \"renderer\": \"northstar:solar_system/mars_sky\",\n"
                + "  \"notes\": {\"text\": \"" + escapeJson(dimension.toString()) + "\"}\n"
                + "}\n";
    }

    private static String planetDimensionJson(ResourceLocation planet, ResourceLocation dimension) {
        return "{\n"
                + "  \"planet\": \"" + planet + "\",\n"
                + "  \"name\": \"surface\",\n"
                + "  \"dimension\": \"" + dimension + "\",\n"
                + "  \"is_orbit\": false,\n"
                + "  \"gravity\": 9.807,\n"
                + "  \"averageTemperature\": 15.0\n"
                + "}\n";
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static final class BridgeWriteException extends RuntimeException {
        private final IOException exception;

        private BridgeWriteException(IOException exception) {
            super(exception);
            this.exception = exception;
        }
    }
}
