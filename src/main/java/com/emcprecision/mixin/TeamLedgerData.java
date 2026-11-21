package com.emcprecision.mixin;

import dev.hex.teamprojecte.GlobalKnowledgeData;
import moze_intel.projecte.emc.DecimalEmc;
import moze_intel.projecte.emc.DecimalEmcLedger;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlobalKnowledgeData.class, remap = false)
public abstract class TeamLedgerData implements DecimalEmcLedger {
    @Unique private int emcprecision$remainder;

    @Override
    public int getEmcRemainder() {
        return emcprecision$remainder;
    }

    @Override
    public void setEmcRemainder(int remainder) {
        emcprecision$remainder = DecimalEmc.normalizeRemainder(remainder);
    }

    @Inject(method = "serialize", at = @At("RETURN"))
    private void emcprecision$write(CallbackInfoReturnable<CompoundTag> callback) {
        if (emcprecision$remainder != 0) {
            callback.getReturnValue().putInt("emc_precision_remainder", emcprecision$remainder);
        }
    }

    @Inject(method = "deserialize", at = @At("TAIL"))
    private void emcprecision$read(CompoundTag tag, CallbackInfo callback) {
        setEmcRemainder(tag.getInt("emc_precision_remainder"));
    }
}
