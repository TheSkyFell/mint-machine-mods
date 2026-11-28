package dev.ryanhcode.sable.physics.callback;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Stub of Sable 2.0.6 (ExplosiveBlockCallback extends it and overrides onHit). */
public class FragileBlockCallback implements BlockSubLevelCollisionCallback {
    protected FragileBlockCallback() {}

    /** Breaks (or, for TNT, primes) the block a ship hit. Both versions return (ZERO, true). */
    public CollisionResult onHit(ServerLevel level, BlockPos pos, BlockState state, Vector3d velocity) {
        throw new AssertionError("stub");
    }
}
