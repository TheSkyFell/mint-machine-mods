package tfar.shippingbin.inventory;

import net.minecraft.world.item.ItemStack;

/** Stub of Shipping Bin 1 (1.21.1): the methods used (in the real interface $slotlessInsertStack is a default method). */
public interface CommonHandler {
    boolean isEmpty();

    int $getSlotCount();

    ItemStack $getStack(int slot);

    int $getMaxStackSize(int slot);

    ItemStack $slotlessInsertStack(ItemStack stack, int count, boolean simulate);

    ItemStack $extractStack(int slot, int amount, boolean simulate);
}
