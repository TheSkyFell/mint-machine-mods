package com.mintmachine.aerofixes;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/mintmachine_aerofixes-common.toml */
final class AeroFixesConfig {
    private AeroFixesConfig() {}

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue TELEPORT_GUARD;
    static final ModConfigSpec.BooleanValue CHORUS_STAFF_TRAIL;
    static final ModConfigSpec.BooleanValue CREATE_TELEPORTERS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("teleports");
        TELEPORT_GUARD = b.comment(
                        "Teleports aimed at a block on a ship (Relics' Chorus Staff, for example) land on the ship instead of in the void,",
                        "and a teleport to a ship that isn't loaded doesn't happen at all")
                .define("guard", true);
        b.pop();
        b.push("relics");
        CHORUS_STAFF_TRAIL = b.comment(
                        "Skip the Chorus Staff's particle trail when its ends are more than 512 blocks apart. Aimed at a ship, the trail",
                        "ran to the ship's stored position about 20 million blocks away and froze the server")
                .define("chorusStaffTrail", true);
        b.pop();
        b.push("createTeleporters");
        CREATE_TELEPORTERS = b.comment(
                        "The Quantum Linker can be aimed at a block on a ship, and an entity teleporter's range counts world distance",
                        "when the teleporter or its target is on a ship (it used to see ships as 20 million blocks away)")
                .define("shipLinks", true);
        b.pop();
        SPEC = b.build();
    }

    static boolean createTeleporters() {
        return get(CREATE_TELEPORTERS);
    }

    static boolean teleportGuard() {
        return get(TELEPORT_GUARD);
    }

    static boolean chorusStaffTrail() {
        return get(CHORUS_STAFF_TRAIL);
    }

    private static boolean get(ModConfigSpec.BooleanValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
