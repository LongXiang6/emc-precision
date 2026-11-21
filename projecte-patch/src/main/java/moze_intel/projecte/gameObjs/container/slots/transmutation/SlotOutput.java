package moze_intel.projecte.gameObjs.container.slots.transmutation;

import java.math.BigInteger;
import moze_intel.projecte.emc.DecimalEmc;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.InventoryContainerSlot;
import moze_intel.projecte.utils.ItemHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SlotOutput extends InventoryContainerSlot {

	private final TransmutationInventory inv;

	public SlotOutput(TransmutationInventory inv, int index, int x, int y) {
		super(inv, index, x, y);
		this.inv = inv;
	}

	@Override
	protected void onSwapCraft(int amount) {
		remove(amount);
	}

	@NotNull
	@Override
	public ItemStack remove(int amount) {
		if (amount == 0) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = ItemHelper.size(getItem(), amount);
		BigInteger emcValue = DecimalEmc.value(stack);
		if (emcValue.signum() <= 0) {
			return ItemStack.EMPTY;
		}
		BigInteger cost = emcValue.multiply(BigInteger.valueOf(amount));
		if (cost.compareTo(inv.getAvailableTenths()) > 0) {
			return ItemStack.EMPTY;
		}
		if (inv.isServer()) {
			inv.removeTenths(cost);
		}
		return stack;
	}

	@Override
	public void initialize(@NotNull ItemStack stack) {
	}

	@Override
	public void set(@NotNull ItemStack stack) {
	}

	@Override
	public boolean mayPlace(@NotNull ItemStack stack) {
		return false;
	}

	@Override
	public boolean mayPickup(@NotNull Player player) {
		return !hasItem() || DecimalEmc.value(getItem()).compareTo(inv.getAvailableTenths()) <= 0;
	}
}
