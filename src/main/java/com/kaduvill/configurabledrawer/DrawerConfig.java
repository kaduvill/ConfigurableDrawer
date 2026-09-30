package com.kaduvill.configurabledrawer;

import java.io.File;
import java.math.BigInteger;

import net.minecraftforge.common.config.Configuration;

public final class DrawerConfig {
    private static final long DEFAULT_CAPACITY = 65_536L;
    public static long maximumCapacity = DEFAULT_CAPACITY;
    public static long defaultCapacity = DEFAULT_CAPACITY;
    private DrawerConfig() { }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        // Forge's integer configuration helpers are only 32-bit. Store decimal strings.
        maximumCapacity = positive(config.get("storage", "maximumCapacity",
                Long.toString(DEFAULT_CAPACITY),
                "Maximum items per drawer. Range: 1 to 9223372036854775807. Default: 65536.\n"
                        + "Copyable limits:\n"
                        + "Max int: 2147483647\n"
                        + "Max long: 9223372036854775807"
        ).getString(), DEFAULT_CAPACITY);
        defaultCapacity = Math.min(maximumCapacity, positive(config.get("storage", "defaultCapacity",
                Long.toString(DEFAULT_CAPACITY),
                "Capacity of new drawers (items). Range: 1 to maximumCapacity. Default: 65536.")
                .getString(), DEFAULT_CAPACITY));
        config.getCategory("storage").get("maximumCapacity").set(Long.toString(maximumCapacity));
        config.getCategory("storage").get("defaultCapacity").set(Long.toString(defaultCapacity));
        if (config.hasChanged()) config.save();
    }
    private static long positive(String text, long fallback) {
        try {
            BigInteger value = new BigInteger(text.trim());
            if (value.signum() <= 0) return fallback;
            return value.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue();
        } catch (NumberFormatException ignored) { return fallback; }
    }
}
