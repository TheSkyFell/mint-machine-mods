package com.simibubi.create.content.logistics.tableCloth;

import com.simibubi.create.content.logistics.packager.InventorySummary;

import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;

/** Stub of Create 6.0.10. The real class extends Item; ShoppingList is a record. */
public class ShoppingListItem {
    public static ShoppingList getList(ItemStack stack) {
        throw new AssertionError("stub");
    }

    public static final class ShoppingList {
        public Couple<InventorySummary> bakeEntries(LevelAccessor level, BlockPos clothPosToIgnore) {
            throw new AssertionError("stub");
        }
    }
}
