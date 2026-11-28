package com.mintmachine.aerofixes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

/**
 * Fixes for Create Aeronautics and Sable in the Create: Mint &amp; Machine modpack. Each part can be switched off in
 * config/mintmachine_aerofixes-common.toml.
 * <ol>
 * <li><b>Teleport guard</b> ({@link TeleportGuard}): a ship's blocks are stored far out in the world, in Sable's
 * "plot grid". Anything that aims a player at a block on a ship, such as Relics' Chorus Staff, gets a position out
 * there. Sable moves such positions to where the ship really is for two of the three ways a player is teleported, but
 * not for {@code teleportTo(level, x, y, z, yaw, pitch)}, so the player ended up in the void (Sable #1667). The guard
 * moves the position onto the ship for all three, and stops the teleport when the ship isn't loaded.</li>
 * </ol>
 */
@Mod(AeroFixes.MOD_ID)
public final class AeroFixes {
    public static final String MOD_ID = "mintmachine_aerofixes";
    static final Logger LOG = LogUtils.getLogger();

    public AeroFixes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AeroFixesConfig.SPEC);
    }
}
