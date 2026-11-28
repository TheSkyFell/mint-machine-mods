package dev.ryanhcode.sable.api;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;

/** Stub of Sable 2.0.6. */
public class SubLevelAssemblyHelper {
    /** Moves blocks between the world and a sub-level's plot (assembly, disassembly, merging). */
    public static void moveBlocks(ServerLevel level, AssemblyTransform transform, Iterable<BlockPos> blocks) {
        throw new AssertionError("stub");
    }

    /** Where each block goes: rotated by angle quarter turns about anchor, then moved to resultingAnchor. */
    public static class AssemblyTransform {
        public AssemblyTransform(BlockPos anchor, BlockPos resultingAnchor, int angle, Rotation rotation, ServerLevel resultingLevel) {
            throw new AssertionError("stub");
        }

        public BlockPos apply(BlockPos pos) {
            throw new AssertionError("stub");
        }
    }
}
