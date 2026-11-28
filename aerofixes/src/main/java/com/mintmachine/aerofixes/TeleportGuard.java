package com.mintmachine.aerofixes;

import org.jetbrains.annotations.Nullable;

import dev.ryanhcode.sable.Sable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Decides what happens to a player teleport whose destination is inside Sable's plot grid, the far-off area where
 * ships keep their blocks. Used by {@link com.mintmachine.aerofixes.mixin.ServerPlayerTeleportMixin}.
 */
public final class TeleportGuard {
    private TeleportGuard() {}

    /**
     * What to do instead of the teleport as asked.
     *
     * @param target where to teleport instead (on the ship, in the world), or null to not teleport at all
     */
    public record Decision(@Nullable Vec3 target) {}

    /**
     * @return null to let the teleport go ahead unchanged (almost always: the destination isn't in the plot grid),
     *         otherwise what to do instead
     */
    public static @Nullable Decision check(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        if (!AeroFixesConfig.teleportGuard() || !inPlotGrid(level, x, z)) return null;

        Vec3 projected = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(x, y, z));
        if (!inPlotGrid(level, projected.x, projected.z)) {
            AeroFixes.LOG.debug("[{}] Moved {}'s teleport to {}, {}, {} onto the ship at {}", AeroFixes.MOD_ID,
                    player.getGameProfile().getName(), x, y, z, projected);
            return new Decision(projected);
        }

        // Still in the plot grid: the ship there isn't loaded, so there's no world position to send the player to
        AeroFixes.LOG.info("[{}] Stopped {} teleporting to {}, {}, {} in {}: that's on a ship that isn't loaded",
                AeroFixes.MOD_ID, player.getGameProfile().getName(), x, y, z, level.dimension().location());
        player.displayClientMessage(Component.translatableWithFallback(
                "message." + AeroFixes.MOD_ID + ".teleport_blocked",
                "You can't teleport there: that ship isn't loaded."), true);
        return new Decision(null);
    }

    private static boolean inPlotGrid(ServerLevel level, double x, double z) {
        return Sable.HELPER.isInPlotGrid(level, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }
}
