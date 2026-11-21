package moze_intel.projecte.emc;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.text.NumberFormat;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.config.ProjectEConfig;
import moze_intel.projecte.utils.TransmutationEMCFormatter;
import moze_intel.projecte.utils.text.PELang;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class DecimalEmc {
    public static final BigInteger SCALE = BigInteger.TEN;
    private static final ThreadLocal<NumberFormat> FORMAT = ThreadLocal.withInitial(() -> {
        NumberFormat format = NumberFormat.getNumberInstance();
        format.setMaximumFractionDigits(1);
        format.setRoundingMode(RoundingMode.DOWN);
        return format;
    });

    private DecimalEmc() {}

    public static BigInteger value(ItemStack stack) {
        return stack.isEmpty() ? BigInteger.ZERO : value(ItemInfo.fromStack(stack));
    }

    public static BigInteger value(ItemInfo info) {
        if (!info.hasModifiedComponents()) {
            return decode(EMCMappingHandler.getStoredEmcValue(info));
        }
        long whole = IEMCProxy.INSTANCE.getValue(info);
        if (whole > 0) {
            return fromWhole(whole);
        }
        long mapped = EMCMappingHandler.getStoredEmcValue(info);
        return mapped > 0 && mapped % 10 != 0 ? decode(mapped) : BigInteger.ZERO;
    }

    private static BigInteger decode(long encoded) {
        return encoded < 0 ? BigInteger.valueOf(encoded).negate().multiply(SCALE) : BigInteger.valueOf(encoded);
    }

    public static BigInteger sellValue(ItemStack stack) {
        return sellValue(value(stack));
    }

    public static BigInteger sellValue(BigInteger tenths) {
        return new BigDecimal(tenths)
                .multiply(BigDecimal.valueOf(ProjectEConfig.server.difficulty.covalenceLoss.get()))
                .toBigInteger();
    }

    public static long whole(long encoded) {
        // A whole-only machine must never buy or burn an item at a different face value.
        return encoded < 0 ? Math.negateExact(encoded) : encoded % 10 == 0 ? encoded / 10 : 0;
    }

    public static int normalizeRemainder(int remainder) {
        return remainder >= 0 && remainder < 10 ? remainder : 0;
    }

    public static BigInteger fromWhole(long whole) {
        return BigInteger.valueOf(whole).multiply(SCALE);
    }

    public static BigInteger balance(IKnowledgeProvider provider) {
        return provider.getEmc().multiply(SCALE).add(BigInteger.valueOf(remainder(provider)));
    }

    public static int remainder(IKnowledgeProvider provider) {
        return provider instanceof DecimalEmcLedger ledger ? ledger.getEmcRemainder() : 0;
    }

    public static void setBalance(IKnowledgeProvider provider, BigInteger balance) {
        BigInteger[] amount = balance.max(BigInteger.ZERO).divideAndRemainder(SCALE);
        if (provider instanceof DecimalEmcLedger ledger) {
            ledger.setEmcRemainder(amount[1].intValueExact());
        } else if (amount[1].signum() != 0) {
            throw new IllegalStateException("Knowledge provider has no fractional ledger");
        }
        provider.setEmc(amount[0]);
    }

    public static String format(BigInteger tenths) {
        return FORMAT.get().format(new BigDecimal(tenths, 1));
    }

    public static Component formatBalance(BigInteger tenths) {
        BigInteger whole = tenths.divide(SCALE);
        return whole.toString().length() > 11
                ? TransmutationEMCFormatter.formatEMC(whole) : Component.literal(format(tenths));
    }

    public static Component tooltip(BigInteger tenths, int count) {
        BigInteger multiplier = BigInteger.valueOf(count);
        String buy = format(tenths.multiply(multiplier));
        BigInteger sellPrice = sellValue(tenths);
        if (sellPrice.equals(tenths)) {
            return (count == 1 ? PELang.EMC_TOOLTIP : PELang.EMC_STACK_TOOLTIP)
                    .translateColored(ChatFormatting.YELLOW, ChatFormatting.WHITE, buy);
        }
        String sell = format(sellPrice.multiply(multiplier));
        return (count == 1 ? PELang.EMC_TOOLTIP_WITH_SELL : PELang.EMC_STACK_TOOLTIP_WITH_SELL)
                .translateColored(ChatFormatting.YELLOW, ChatFormatting.WHITE, buy, ChatFormatting.BLUE, sell);
    }
}
