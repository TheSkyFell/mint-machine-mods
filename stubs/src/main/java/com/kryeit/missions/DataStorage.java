package com.kryeit.missions;

import java.util.Map;
import java.util.UUID;

import com.kryeit.missions.config.ConfigReader;

/**
 * Stub of Missions 0.5.2 (Kryeit, 1.21.1), for the mixin target only. The real class also has the private
 * {@code ListTag getActiveMissionsTag(UUID)} that the mixin shadows.
 */
public class DataStorage {
    private DataStorage() {}

    public void reassignActiveMissions(Map<MissionType, ConfigReader.MissionTypeConfig> missions, UUID player) {
        throw new AssertionError("stub");
    }

    public void reassignActiveMission(Map<MissionType, ConfigReader.MissionTypeConfig> missions, UUID player, int index) {
        throw new AssertionError("stub");
    }
}
