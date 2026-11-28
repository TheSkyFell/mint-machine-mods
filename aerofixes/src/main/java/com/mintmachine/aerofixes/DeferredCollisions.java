package com.mintmachine.aerofixes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.joml.Vector3d;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback.CollisionResult;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server hangs when a ship hits leaves, crops, melons, ice or TNT (aeronautics_review item 2). Sable's
 * {@code FragileBlockCallback} (and {@code ExplosiveBlockCallback}, for TNT) breaks or primes the block from inside the
 * physics step that found the collision. The block change goes straight back into the physics engine in the middle of
 * that step, which never returns, and the server freezes (Sable #1603, #1673, #1510; Simulated #1453, #1395).
 * <p>
 * Here the break is queued instead, and the callback returns what it would have returned (the ship passes through that
 * block for this contact). The queue runs at the end of the server tick, once the step has finished, and only for
 * blocks that are still what was hit. Used by {@link com.mintmachine.aerofixes.mixin.sable.FragileBlockCallbackMixin}.
 */
public final class DeferredCollisions {
    private DeferredCollisions() {}

    private record Hit(FragileBlockCallback callback, ServerLevel level, BlockPos pos, BlockState state, Vector3d velocity) {}

    private record Key(ServerLevel level, long pos) {}

    private static final Map<Key, Hit> QUEUE = new LinkedHashMap<>();

    public static CollisionResult onHit(FragileBlockCallback callback, ServerLevel level, BlockPos pos, BlockState state, Vector3d velocity) {
        if (!AeroFixesConfig.deferredCollisions()) return callback.onHit(level, pos, state, velocity);
        BlockPos at = pos.immutable();
        synchronized (QUEUE) {
            QUEUE.putIfAbsent(new Key(level, at.asLong()), new Hit(callback, level, at, state, new Vector3d(velocity)));
        }
        return new CollisionResult(new Vector3d(), true);
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        Hit[] hits;
        synchronized (QUEUE) {
            if (QUEUE.isEmpty() || SubLevelPhysicsSystem.getCurrentlySteppingSystem() != null) return;
            hits = QUEUE.values().toArray(new Hit[0]);
            QUEUE.clear();
        }
        for (Hit hit : hits) {
            try {
                if (!hit.level().isLoaded(hit.pos()) || hit.level().getBlockState(hit.pos()) != hit.state()) continue;
                hit.callback().onHit(hit.level(), hit.pos(), hit.state(), hit.velocity());
            } catch (RuntimeException e) {
                AeroFixes.LOG.warn("[{}] A ship's collision with {} at {} failed", AeroFixes.MOD_ID, hit.state(), hit.pos(), e);
            }
        }
    }
}
