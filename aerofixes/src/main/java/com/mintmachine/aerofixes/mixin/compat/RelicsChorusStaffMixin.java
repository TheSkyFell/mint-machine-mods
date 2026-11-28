package com.mintmachine.aerofixes.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mintmachine.aerofixes.ChorusStaffTrail;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Relics 0.12.11: the Chorus Staff's blink trail ({@code ChorusStaffItem.use} schedules it as {@code lambda$use$7},
 * one particle point per block from start to end) is skipped when its ends are absurdly far apart. See
 * {@link ChorusStaffTrail}. The lambda's name belongs to this Relics version: if a Relics update renames it, this
 * config (not required) logs a mixin error at startup and the game carries on without it.
 */
@Mixin(targets = "it.hurts.sskirillss.relics.items.relics.ChorusStaffItem", remap = false)
public abstract class RelicsChorusStaffMixin {
    @Inject(method = "lambda$use$7(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void mintmachine_aerofixes$skipFarTrail(Vec3 from, Vec3 to, RandomSource random, Level level,
                                                          Player player, ItemStack stack, CallbackInfo ci) {
        if (ChorusStaffTrail.shouldSkip(from, to)) ci.cancel();
    }
}
