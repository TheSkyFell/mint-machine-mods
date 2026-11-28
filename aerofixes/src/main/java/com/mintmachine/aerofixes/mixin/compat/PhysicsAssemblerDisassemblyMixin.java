package com.mintmachine.aerofixes.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mintmachine.aerofixes.DisassemblyCheck;
import com.simibubi.create.content.contraptions.AssemblyException;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Simulated 1.3.3 (Create Aeronautics): the Physics Assembler's {@code throwDisassemblyExceptions}, which runs when
 * disassembly starts and again just before the blocks are placed, also refuses when blocks are in the way. See
 * {@link DisassemblyCheck}.
 */
@Mixin(targets = "dev.simulated_team.simulated.content.blocks.physics_assembler.PhysicsAssemblerBlockEntity", remap = false)
public abstract class PhysicsAssemblerDisassemblyMixin {
    @Shadow(remap = false)
    private int disassemblyAngle;

    @Inject(method = "throwDisassemblyExceptions", at = @At("TAIL"), remap = false)
    private void mintmachine_aerofixes$checkInTheWay(ServerSubLevel ship, CallbackInfo ci) throws AssemblyException {
        DisassemblyCheck.check((BlockEntity) (Object) this, ship, disassemblyAngle);
    }
}
