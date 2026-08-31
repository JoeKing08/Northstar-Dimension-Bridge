package com.joeking.northstarbridge;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public final class BridgeConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("Exact dimension IDs to register. Empty means all eligible dimensions.")
                .push("filter");
        WHITELIST = builder.defineListAllowEmpty(
                "whitelist",
                List.of(),
                value -> value instanceof String && ResourceLocationUtil.isValid((String) value)
        );
        BLACKLIST = builder.defineList(
                "blacklist",
                List.of("minecraft:the_nether", "minecraft:the_end"),
                value -> value instanceof String && ResourceLocationUtil.isValid((String) value)
        );
        builder.pop();
        SCAN_INTERVAL_TICKS = builder.comment("How often the server checks for newly registered dimensions.")
                .defineInRange("scan_interval_ticks", 20, 1, 20 * 60);
        SPEC = builder.build();
    }

    private BridgeConfig() {
    }
}
