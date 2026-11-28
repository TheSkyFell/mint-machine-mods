package dev.ryanhcode.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Stub of Sable 2.0.6 (the two methods the Aeronautics fixes call). */
public class ActiveSableCompanion {
    /** A position inside a loaded sub-level's plot, moved to where that block is in the world; anything else as is. */
    public Vec3 projectOutOfSubLevel(Level level, Vec3 pos) {
        throw new AssertionError("stub");
    }

    /** Whether a chunk (chunk coordinates) is inside the level's plot grid, where sub-levels keep their blocks. */
    public boolean isInPlotGrid(Level level, int chunkX, int chunkZ) {
        throw new AssertionError("stub");
    }
}
