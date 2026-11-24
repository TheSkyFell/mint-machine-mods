package com.kryeit.missions.config;

import java.util.List;
import java.util.Map;

import com.kryeit.missions.MissionType;

/** Stub of Missions 0.5.2 (Kryeit, 1.21.1). The three nested types are records in the real class. */
public class ConfigReader {
    private ConfigReader() {}

    public Map<MissionType, MissionTypeConfig> getMissions() {
        throw new AssertionError("stub");
    }

    public static final class MissionTypeConfig {
        private MissionTypeConfig() {}

        public MissionType missionType() {
            throw new AssertionError("stub");
        }

        public float weight() {
            throw new AssertionError("stub");
        }

        public Map<Map<String, Range>, SubMissionConfig> subMissions() {
            throw new AssertionError("stub");
        }
    }

    public static final class SubMissionConfig {
        private SubMissionConfig() {}

        public List<String> titles() {
            throw new AssertionError("stub");
        }

        public Map<String, Range> items() {
            throw new AssertionError("stub");
        }

        public List<Reward> rewards() {
            throw new AssertionError("stub");
        }
    }

    public static final class Reward {
        private Reward() {}

        public String item() {
            throw new AssertionError("stub");
        }

        public Range count() {
            throw new AssertionError("stub");
        }
    }
}
