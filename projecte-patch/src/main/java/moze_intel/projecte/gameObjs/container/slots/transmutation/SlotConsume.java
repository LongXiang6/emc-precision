package moze_intel.projecte.gameObjs.container.slots.transmutation;

import java.math.BigInteger;
import moze_intel.projecte.emc.DecimalEmc;
import moze_intel.projecte.gameObjs.container.inventory.TransmutationInventory;
import moze_intel.projecte.gameObjs.container.slots.InventoryContainerSlot;
import moze_intel.projecte.gameObjs.registries.PEItems;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SlotConsume extends InventoryContainerSlot {

	private final TransmutationInventory inv;

	public SlotConsume(TransmutationInventory inv, int index, int x, int y) {
		super(inv, index, x, y);
		this.inv = inv;
	}

	@Override
	public void initialize(@NotNull ItemStack stack) {
		//Note: We don't need to copy any of the logic from set as initialize is only ever called on the client
	}

	@Override
	public void set(@NotNull ItemStack stack) {
		if (inv.isServer() && !stack.isEmpty()) {
			inv.handleKnowledge(stack);
			inv.addTenths(DecimalEmc.sellValue(stack).multiply(BigInteger.valueOf(stack.getCount())));
			this.setChanged();
		}
	}

	@Override
	public boolean mayPlace(@NotNull ItemStack stack) {
		return DecimalEmc.sellValue(stack).signum() > 0 || stack.is(PEItems.TOME_OF_KNOWLEDGE);
	}
}
