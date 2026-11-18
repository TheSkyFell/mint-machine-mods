package dev.ithundxr.createnumismatics.content.bank;

import java.util.UUID;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Stub of Create: Numismatics 1.1.0. */
public abstract class CardItem extends Item {
    private CardItem() {
        super(null);
    }

    public static boolean isBound(ItemStack stack) {
        throw new AssertionError("stub");
    }

    public static UUID get(ItemStack stack) {
        throw new AssertionError("stub");
    }
}
