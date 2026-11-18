package com.mintmachine.accelerators;

import com.hollingsworth.arsnouveau.api.source.SourcelinkEventQueue;
import com.hollingsworth.arsnouveau.common.block.tile.AgronomicSourcelinkTile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Ars Nouveau's Agronomic Sourcelink event queue. Only touched when those classes exist. */
final class ArsCompat {
    private ArsCompat() {}

    static final String QUEUE_CLASS = "com.hollingsworth.arsnouveau.api.source.SourcelinkEventQueue";
    static final String LINK_CLASS = "com.hollingsworth.arsnouveau.common.block.tile.AgronomicSourcelinkTile";

    static void addSource(Level level, int amount, BlockPos pos) {
        SourcelinkEventQueue.addManaEvent(level, AgronomicSourcelinkTile.class, amount, null, pos);
    }
}
