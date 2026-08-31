package com.joeking.northstarbridge;

import net.minecraft.resources.ResourceLocation;

final class ResourceLocationUtil {
    private ResourceLocationUtil() {
    }

    static boolean isValid(String value) {
        return ResourceLocation.tryParse(value) != null;
    }
}
