package com.mintmachine.aerofixes;

import net.minecraft.world.phys.Vec3;

/**
 * Relics' Chorus Staff draws a particle trail one tick after a blink, one point per block between where the player was
 * and where the blink was aimed. Aimed at a ship, that's a position in Sable's plot grid about 20 million blocks away,
 * so the trail had ~20 million points and the server tick never finished (seen 11-28: the integrated server hung in
 * {@code ChorusStaffItem.lambda$use$7} until the game was killed). The teleport itself is fixed by
 * {@link TeleportGuard}; this skips the trail when its ends are further apart than any blink can reach. Used by
 * {@link com.mintmachine.aerofixes.mixin.compat.RelicsChorusStaffMixin}.
 */
public final class ChorusStaffTrail {
    private ChorusStaffTrail() {}

    /** Far beyond the staff's reach (its blink distance is tens of blocks), and still a quick trail to draw. */
    static final double MAX_LENGTH = 512;

    public static boolean shouldSkip(Vec3 from, Vec3 to) {
        if (!AeroFixesConfig.chorusStaffTrail()) return false;
        if (from.distanceToSqr(to) <= MAX_LENGTH * MAX_LENGTH) return false;
        AeroFixes.LOG.debug("[{}] Skipped a Chorus Staff trail from {} to {}", AeroFixes.MOD_ID, from, to);
        return true;
    }
}
