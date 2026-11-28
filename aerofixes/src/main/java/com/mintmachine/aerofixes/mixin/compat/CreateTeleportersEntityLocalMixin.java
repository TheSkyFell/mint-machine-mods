package com.mintmachine.aerofixes.mixin.compat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.mintmachine.aerofixes.CreateTeleportersCompat;

import net.minecraft.world.entity.Entity;

/**
 * Create Teleporters Remastered 3.0.2: {@code SableAeronauticsIntegration.teleportEntity(Entity, ServerLevel, x, y, z,
 * yaw)} calls Sable's {@code SubLevelHelper.popEntityLocal} before the teleport and {@code pushEntityLocal} after it.
 * Those are a pair meant to wrap a short calculation: push moves the entity into the ship's stored (plot-grid)
 * coordinates and pop moves it back. Here nothing had pushed before the pop, and nothing pops after the push, so a
 * player teleported onto a ship was left standing at the ship's stored position, about 20 million blocks out. The next
 * look at the block under them made the server generate terrain there, which crashed it (seen 11-28). The teleport
 * itself already lands on the ship's real position, so both calls are skipped.
 */
@Mixin(targets = "net.createteleporters.integration.SableAeronauticsIntegration", remap = false)
public abstract class CreateTeleportersEntityLocalMixin {
    private static final String TELEPORT =
            "teleportEntity(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/server/level/ServerLevel;DDDF)V";

    @Redirect(method = TELEPORT, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/createteleporters/integration/SableAeronauticsIntegration;popEntityLocal(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V"))
    private static void mintmachine_aerofixes$skipPop(Object subLevel, Entity entity) {
        CreateTeleportersCompat.entityLocal("popEntityLocal", subLevel, entity);
    }

    @Redirect(method = TELEPORT, remap = false, at = @At(value = "INVOKE",
            target = "Lnet/createteleporters/integration/SableAeronauticsIntegration;pushEntityLocal(Ljava/lang/Object;Lnet/minecraft/world/entity/Entity;)V"))
    private static void mintmachine_aerofixes$skipPush(Object subLevel, Entity entity) {
        CreateTeleportersCompat.entityLocal("pushEntityLocal", subLevel, entity);
    }
}
