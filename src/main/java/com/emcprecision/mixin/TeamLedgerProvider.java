package com.emcprecision.mixin;

import dev.hex.teamprojecte.GlobalKnowledgeData;
import dev.hex.teamprojecte.GlobalKnowledgeProvider;
import java.math.BigInteger;
import moze_intel.projecte.emc.DecimalEmcLedger;
import moze_intel.projecte.impl.capability.KnowledgeImpl.KnowledgeAttachment;
import moze_intel.projecte.network.packets.IPEPacket;
import moze_intel.projecte.network.packets.to_client.knowledge.KnowledgeSyncEmcPKT;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlobalKnowledgeProvider.class, remap = false)
public abstract class TeamLedgerProvider implements DecimalEmcLedger {
    @Shadow public abstract BigInteger getEmc();

    @Shadow private GlobalKnowledgeData getGlobalData() {
        throw new AssertionError();
    }

    @Shadow private static void sendPacket(IPEPacket packet, ServerPlayer player) {
        throw new AssertionError();
    }

    @Override
    public int getEmcRemainder() {
        return ((DecimalEmcLedger) getGlobalData()).getEmcRemainder();
    }

    @Override
    public void setEmcRemainder(int remainder) {
        ((DecimalEmcLedger) getGlobalData()).setEmcRemainder(remainder);
    }

    @Inject(method = "createAttachment", at = @At("RETURN"))
    private static void emcprecision$includeRemainder(GlobalKnowledgeProvider provider,
            CallbackInfoReturnable<KnowledgeAttachment> callback) {
        callback.getReturnValue().setEmcRemainder(((DecimalEmcLedger) provider).getEmcRemainder());
    }

    @Inject(method = "syncEmc", at = @At("HEAD"), cancellable = true)
    private void emcprecision$syncRemainder(ServerPlayer player, CallbackInfo callback) {
        sendPacket(new KnowledgeSyncEmcPKT(getEmc(), getEmcRemainder()), player);
        callback.cancel();
    }
}
