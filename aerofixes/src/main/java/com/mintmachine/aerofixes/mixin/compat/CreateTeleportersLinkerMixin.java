package com.mintmachine.aerofixes.mixin.compat;

import java.util.function.BiFunction;
import java.util.function.Function;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mintmachine.aerofixes.CreateTeleportersCompat;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;

/**
 * Create Teleporters Remastered 3.0.2: the Quantum Linker can be aimed at a block on a ship. Its
 * {@code TpLinkRightclickedProcedure.saveTarget} (also used by the advanced linker) searches the world's blocks itself
 * and looked straight through ships. See {@link CreateTeleportersCompat#shipAwareLinkTarget}.
 */
@Mixin(targets = "net.createteleporters.procedures.TpLinkRightclickedProcedure", remap = false)
public abstract class CreateTeleportersLinkerMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "saveTarget", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/BlockGetter;traverseBlocks(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Ljava/lang/Object;Ljava/util/function/BiFunction;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private static Object mintmachine_aerofixes$includeShips(Vec3 from, Vec3 to, Object context, BiFunction tester,
                                                             Function onFail, Entity entity, ItemStack stack, int slot,
                                                             boolean flag) {
        Object found = BlockGetter.traverseBlocks(from, to, context, tester, onFail);
        return CreateTeleportersCompat.shipAwareLinkTarget(entity, from, to, found);
    }
}
