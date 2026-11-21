package com.emcprecision;

import moze_intel.projecte.emc.DecimalEmc;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

@Mod("emc_precision")
public final class EmcPrecision {
    private static final ModConfigSpec.BooleanValue AUTO_PRICE;
    private static final ModConfigSpec CONFIG;

    static {
        var builder = new ModConfigSpec.Builder();
        AUTO_PRICE = builder.comment("Estimate missing default-item prices after ProjectE has mapped its recipes.",
                "ProjectE's pregenerated price cache is bypassed when this is enabled.")
                .define("autoPriceMissingItems", true);
        CONFIG = builder.build();
    }

    public EmcPrecision(ModContainer container) {
        // Prevent a later ProjectE replacement from loading with integer pricing.
        DecimalEmc.normalizeRemainder(0);
        container.registerConfig(ModConfig.Type.COMMON, CONFIG);
    }

    public static boolean autoPrice() {
        return AUTO_PRICE.get();
    }
}
