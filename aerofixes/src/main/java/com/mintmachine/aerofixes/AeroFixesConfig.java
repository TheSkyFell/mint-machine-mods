package com.mintmachine.aerofixes;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/mintmachine_aerofixes-common.toml */
final class AeroFixesConfig {
    private AeroFixesConfig() {}

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.BooleanValue TELEPORT_GUARD;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("teleports");
        TELEPORT_GUARD = b.comment(
                        "Teleports aimed at a block on a ship (Relics' Chorus Staff, for example) land on the ship instead of in the void,",
                        "and a teleport to a ship that isn't loaded doesn't happen at all")
                .define("guard", true);
        b.pop();
        SPEC = b.build();
    }

    static boolean teleportGuard() {
        try {
            return TELEPORT_GUARD.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }
}
