package com.mintmachine.aerofixes.mixin.sable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mintmachine.aerofixes.AssemblyDropGuard;

import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Sable 2.0.6: turns the drop guard on for the whole of {@code moveBlocks}. See {@link AssemblyDropGuard}. */
@Mixin(value = SubLevelAssemblyHelper.class, remap = false)
public abstract class SubLevelAssemblyHelperMixin {
    @Inject(method = "moveBlocks", at = @At("HEAD"), remap = false)
    private static void mintmachine_aerofixes$beginMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                       Iterable<BlockPos> blocks, CallbackInfo ci) {
        AssemblyDropGuard.begin(level, blocks);
    }

    @Inject(method = "moveBlocks", at = @At("RETURN"), remap = false)
    private static void mintmachine_aerofixes$endMove(ServerLevel level, SubLevelAssemblyHelper.AssemblyTransform transform,
                                                     Iterable<BlockPos> blocks, CallbackInfo ci) {
        AssemblyDropGuard.end();
    }
}
