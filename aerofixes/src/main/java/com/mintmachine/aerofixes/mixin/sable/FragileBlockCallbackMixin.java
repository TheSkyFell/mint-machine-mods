package com.mintmachine.aerofixes.mixin.sable;

import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mintmachine.aerofixes.DeferredCollisions;

import dev.ryanhcode.sable.api.physics.callback.BlockSubLevelCollisionCallback;
import dev.ryanhcode.sable.physics.callback.FragileBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sable 2.0.6: {@code FragileBlockCallback.sable$onCollision} (also used by {@code ExplosiveBlockCallback}) queues its
 * {@code onHit} until the physics step has finished. See {@link DeferredCollisions}.
 */
@Mixin(value = FragileBlockCallback.class, remap = false)
public abstract class FragileBlockCallbackMixin {
    @Redirect(method = "sable$onCollision", remap = false, at = @At(value = "INVOKE",
            target = "Ldev/ryanhcode/sable/physics/callback/FragileBlockCallback;onHit(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lorg/joml/Vector3d;)Ldev/ryanhcode/sable/api/physics/callback/BlockSubLevelCollisionCallback$CollisionResult;"))
    private BlockSubLevelCollisionCallback.CollisionResult mintmachine_aerofixes$deferHit(FragileBlockCallback self, ServerLevel level,
                                                                                       BlockPos pos, BlockState state, Vector3d velocity) {
        return DeferredCollisions.onHit(self, level, pos, state, velocity);
    }
}
