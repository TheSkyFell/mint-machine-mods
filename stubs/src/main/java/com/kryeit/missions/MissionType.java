package com.kryeit.missions;

/** Stub of Missions 0.5.2 (Kryeit, 1.21.1). The real interface also has progress, data and display methods. */
public interface MissionType {
    String id();

    default boolean assignOnlyOnce() {
        throw new AssertionError("stub");
    }
}
