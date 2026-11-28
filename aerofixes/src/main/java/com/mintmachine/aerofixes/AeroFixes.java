package com.mintmachine.aerofixes;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Fixes for Create Aeronautics and Sable in the Create: Mint &amp; Machine modpack. Each part can be switched off in
 * config/mintmachine_aerofixes-common.toml.
 * <ol>
 * <li><b>Drop guard</b> ({@link AssemblyDropGuard}): building or taking apart a ship no longer duplicates the contents
 * of its blocks.</li>
 * <li><b>Deferred collisions</b> ({@link DeferredCollisions}): ships smashing leaves, crops, melons, ice or TNT no
 * longer freeze the server.</li>
 * <li><b>Disassembly check</b> ({@link DisassemblyCheck}): a ship can't be taken apart into blocks that are in the
 * way.</li>
 * <li><b>Teleport guard</b> ({@link TeleportGuard}): a ship's blocks are stored far out in the world, in Sable's
 * "plot grid". Anything that aims a player at a block on a ship, such as Relics' Chorus Staff, gets a position out
 * there. Sable moves such positions to where the ship really is for two of the three ways a player is teleported, but
 * not for {@code teleportTo(level, x, y, z, yaw, pitch)}, so the player ended up in the void (Sable #1667). The guard
 * moves the position onto the ship for all three, and stops the teleport when the ship isn't loaded.</li>
 * <li><b>Chorus Staff trail</b> ({@link ChorusStaffTrail}): the staff's particle trail still ran to the far-off
 * position, one point per block, and froze the server. It's skipped when its ends are over 512 blocks apart.</li>
 * <li><b>Create Teleporters</b> ({@link CreateTeleportersCompat}): the Quantum Linker can be aimed at a block on a
 * ship, the entity teleporter's range check counts world distance when either end is on a ship, and its teleport no
 * longer leaves the player at the ship's stored position.</li>
 * <li><b>Stranded players</b> ({@link StrandedPlayers}): a player saved inside the ships' storage area is moved to the
 * world spawn as they join, instead of crashing the server.</li>
 * </ol>
 */
@Mod(AeroFixes.MOD_ID)
public final class AeroFixes {
    public static final String MOD_ID = "mintmachine_aerofixes";
    static final Logger LOG = LogUtils.getLogger();

    public AeroFixes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AeroFixesConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(StrandedPlayers::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(AssemblyDropGuard::onServerTick);
        NeoForge.EVENT_BUS.addListener(DeferredCollisions::onServerTick);
    }
}
