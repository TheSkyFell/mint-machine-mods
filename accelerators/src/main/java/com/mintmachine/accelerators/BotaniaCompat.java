package com.mintmachine.accelerators;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import velrondevs.botania.common.block.flower.functional.AgricarnationBlockEntity;

/** Botania Revived's Agricarnation. Only touched when that class exists. */
final class BotaniaCompat {
    private BotaniaCompat() {}

    static final String CLASS = "velrondevs.botania.common.block.flower.functional.AgricarnationBlockEntity";

    static boolean isAgricarnation(BlockEntity be) {
        return be instanceof AgricarnationBlockEntity;
    }

    /** Whether the flower's area covers pos, it isn't switched off by redstone, and it can pay. */
    static boolean canHelp(BlockEntity be, BlockPos pos, int mana) {
        AgricarnationBlockEntity f = (AgricarnationBlockEntity) be;
        BlockPos c = f.getEffectivePos();
        int r = f.getRange();
        int dy = pos.getY() - c.getY();
        return Math.abs(pos.getX() - c.getX()) <= r && Math.abs(pos.getZ() - c.getZ()) <= r && dy >= -1 && dy <= 4
                && f.redstoneSignal == 0 && f.getMana() >= mana;
    }

    static void pay(BlockEntity be, int mana) {
        ((AgricarnationBlockEntity) be).addMana(-mana);
    }
}
