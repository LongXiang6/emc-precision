package com.emcprecision.mixin;

import com.emcprecision.EmcPrecision;
import com.emcprecision.MissingPriceGraph;
import moze_intel.projecte.api.mapper.arithmetic.IValueArithmetic;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import moze_intel.projecte.config.MappingConfig;
import moze_intel.projecte.emc.EMCMappingHandler;
import moze_intel.projecte.emc.SimpleGraphMapper;
import moze_intel.projecte.shaded.org.apache.commons.math3.fraction.BigFraction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = EMCMappingHandler.class, remap = false)
abstract class MappingHandler {
    @Redirect(method = "map", at = @At(value = "NEW", target = "moze_intel/projecte/emc/SimpleGraphMapper"))
    private static SimpleGraphMapper<NormalizedSimpleStack, BigFraction, IValueArithmetic<BigFraction>> precisionGraph(
            IValueArithmetic<BigFraction> arithmetic) {
        if (EmcPrecision.autoPrice()) {
            return new MissingPriceGraph(arithmetic);
        }
        return new SimpleGraphMapper<>(arithmetic);
    }

    @Redirect(method = "map", at = @At(value = "INVOKE",
            target = "Lmoze_intel/projecte/config/MappingConfig;usePregenerated()Z"))
    private static boolean usePregenerated() {
        return !EmcPrecision.autoPrice() && MappingConfig.usePregenerated();
    }
}
