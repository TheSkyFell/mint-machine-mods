package com.mintmachine.missionpaths;

import com.kryeit.Missions;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Missions Paths: splits the Missions mod's 10 weekly missions evenly between paths (by default 2 each of Create,
 * Travel, Magic, Farming and Fishing), and keeps rerolls inside a mission's path. The work is done by
 * {@link com.mintmachine.missionpaths.mixin.DataStorageMixin}; this class only reports the setup when a world starts.
 */
@Mod(MissionPaths.MOD_ID)
public final class MissionPaths {
    public static final String MOD_ID = "missions_paths";
    private static final Logger LOG = LogUtils.getLogger();

    public MissionPaths() {
        NeoForge.EVENT_BUS.addListener(ServerStartedEvent.class, e -> {
            if (Missions.getConfig() == null) {
                LOG.warn("[missions_paths] The Missions mod has no config loaded; nothing to do");
                return;
            }
            PathAssigner.logSummary(Missions.getConfig().getMissions());
        });
    }
}
