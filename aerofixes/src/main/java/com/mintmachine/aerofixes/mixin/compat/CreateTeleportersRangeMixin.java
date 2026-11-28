package com.mintmachine.aerofixes.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mintmachine.aerofixes.CreateTeleportersCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Create Teleporters Remastered 3.0.2: the entity teleporter's range check compares world distances when the
 * teleporter or its target is on a ship. Its {@code EntityTeleporterOnTickUpdateProcedure.execute} reads the range
 * once, from {@code CTPConfigConfiguration.ENTITY_TP_RANGE}, for both of its checks. See
 * {@link CreateTeleportersCompat#shipAwareRange}.
 */
@Mixin(targets = "net.createteleporters.procedures.EntityTeleporterOnTickUpdateProcedure", remap = false)
public abstract class CreateTeleportersRangeMixin {
    @Shadow(remap = false)
    private static ItemStack itemFromBlockInventory(LevelAccessor world, BlockPos pos, int slot) {
        throw new AssertionError();
    }

    @Redirect(method = "execute", remap = false, at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/common/ModConfigSpec$ConfigValue;get()Ljava/lang/Object;"))
    private static Object mintmachine_aerofixes$shipAwareRange(ModConfigSpec.ConfigValue<?> rangeSetting, LevelAccessor world,
                                                               double x, double y, double z) {
        Object range = rangeSetting.get();
        ItemStack linker = itemFromBlockInventory(world, BlockPos.containing(x, y, z), 0);
        return CreateTeleportersCompat.shipAwareRange(range, world, x, y, z, linker);
    }
}
