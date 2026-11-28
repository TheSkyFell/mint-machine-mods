package com.mintmachine.aerofixes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mintmachine.aerofixes.StrandedPlayers;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Moves a player saved inside Sable's plot grid to the world spawn as their data is read. See {@link StrandedPlayers}. */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerRescueMixin {
    @Inject(method = "readAdditionalSaveData(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"))
    private void mintmachine_aerofixes$rescueStranded(CompoundTag tag, CallbackInfo ci) {
        StrandedPlayers.rescueIfStranded((ServerPlayer) (Object) this, tag);
    }
}
