package com.mintmachine.aerofixes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mintmachine.aerofixes.AssemblyDropGuard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Discards drops at the old positions of a ship block move in progress. See {@link AssemblyDropGuard}. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelDropGuardMixin {
    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void mintmachine_aerofixes$dropGuard(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (AssemblyDropGuard.shouldDiscard((ServerLevel) (Object) this, entity)) cir.setReturnValue(false);
    }
}
