package com.mintmachine.aerofixes;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Item dupes when a ship is built or taken apart (aeronautics_review item 1). Sable's
 * {@code SubLevelAssemblyHelper.moveBlocks} copies every block, with its block entity's data, to its new place, then
 * sets the old places to air. Anything that drops its contents when removed (Create's Item Drain, Table Cloths, Brass
 * Tunnel, Mechanical Crafter, Simulated's Portable Engines, ...) and isn't cleared first drops them there, while the
 * copy keeps them too. Blocks with nothing left to stand on pop off the same way, and a ship whose last solid block is
 * cleared destroys what's left with drops.
 * <p>
 * While {@code moveBlocks} runs, this remembers the old positions (and the block above each). Item and experience
 * drops that appear there in that level are discarded, since everything at an old position has just been copied.
 * Drops anywhere else (a torch on a wall next to the ship) still happen. Used by
 * {@link com.mintmachine.aerofixes.mixin.sable.SubLevelAssemblyHelperMixin} and
 * {@link com.mintmachine.aerofixes.mixin.ServerLevelDropGuardMixin}.
 */
public final class AssemblyDropGuard {
    private AssemblyDropGuard() {}

    private static ServerLevel level;
    private static final LongOpenHashSet POSITIONS = new LongOpenHashSet();
    private static int depth;
    private static int discarded;

    public static void begin(ServerLevel in, Iterable<BlockPos> blocks) {
        if (depth == 0) {
            level = in;
            POSITIONS.clear();
            discarded = 0;
        }
        depth++;
        if (in != level || !AeroFixesConfig.dropGuard()) return;
        for (BlockPos pos : blocks) {
            POSITIONS.add(pos.asLong());
            POSITIONS.add(BlockPos.asLong(pos.getX(), pos.getY() + 1, pos.getZ()));
        }
    }

    public static void end() {
        if (depth == 0 || --depth > 0) return;
        if (discarded > 0) {
            AeroFixes.LOG.info("[{}] Discarded {} item/experience drops left at the old block positions of a ship being built or taken apart",
                    AeroFixes.MOD_ID, discarded);
        }
        reset();
    }

    /** @return true if this entity is a drop at an old position of a move in progress */
    public static boolean shouldDiscard(ServerLevel in, Entity entity) {
        if (depth == 0 || in != level || !(entity instanceof ItemEntity || entity instanceof ExperienceOrb)) return false;
        if (!POSITIONS.contains(BlockPos.containing(entity.position()).asLong())) return false;
        discarded++;
        return true;
    }

    /** Safety net: if a move ever threw before finishing, don't keep eating drops. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (depth != 0) {
            AeroFixes.LOG.warn("[{}] A ship block move didn't finish cleanly; resetting the drop guard", AeroFixes.MOD_ID);
            reset();
        }
    }

    private static void reset() {
        depth = 0;
        level = null;
        POSITIONS.clear();
        discarded = 0;
    }
}
