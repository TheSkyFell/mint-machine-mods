package com.mintmachine.aerofixes.mixin;

import java.util.Set;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mintmachine.aerofixes.TeleportGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;

/**
 * The teleport guard ({@link TeleportGuard}) on all three ways a player is teleported.
 * <p>
 * Sable 2.0.6 already moves plot-grid positions onto the ship for {@code teleportTo(x, y, z)} and
 * {@code teleportTo(level, x, y, z, relative, yaw, pitch)}. For those two this only stops the teleport when the ship
 * isn't loaded. {@code teleportTo(level, x, y, z, yaw, pitch)} gets no help from Sable, so here the position is moved
 * onto the ship too; that's the one Relics' Chorus Staff uses. Either way it works whether Sable's own wrapper runs
 * before or after this.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportMixin {
    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void mintmachine_aerofixes$guardTeleportToLevel(ServerLevel level, double x, double y, double z,
                                                           float yaw, float pitch, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        TeleportGuard.Decision decision = TeleportGuard.check(self, level, x, y, z);
        if (decision == null) return;
        ci.cancel();
        if (decision.target() != null) {
            // The new position is in the world, so this call goes straight through
            self.teleportTo(level, decision.target().x, decision.target().y, decision.target().z, yaw, pitch);
        }
    }

    @Inject(method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z", at = @At("HEAD"),
            cancellable = true)
    private void mintmachine_aerofixes$guardTeleportToRelative(ServerLevel level, double x, double y, double z,
                                                              Set<RelativeMovement> relative, float yaw, float pitch,
                                                              CallbackInfoReturnable<Boolean> cir) {
        // A relative x, y or z is an offset, not a position
        if (relative.contains(RelativeMovement.X) || relative.contains(RelativeMovement.Y)
                || relative.contains(RelativeMovement.Z)) {
            return;
        }
        ServerPlayer self = (ServerPlayer) (Object) this;
        TeleportGuard.Decision decision = TeleportGuard.check(self, level, x, y, z);
        if (decision == null) return;
        cir.setReturnValue(decision.target() != null
                && self.teleportTo(level, decision.target().x, decision.target().y, decision.target().z, relative, yaw, pitch));
    }

    @Inject(method = "teleportTo(DDD)V", at = @At("HEAD"), cancellable = true)
    private void mintmachine_aerofixes$guardTeleportTo(double x, double y, double z, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        TeleportGuard.Decision decision = TeleportGuard.check(self, self.serverLevel(), x, y, z);
        if (decision == null) return;
        ci.cancel();
        if (decision.target() != null) {
            self.teleportTo(decision.target().x, decision.target().y, decision.target().z);
        }
    }
}
