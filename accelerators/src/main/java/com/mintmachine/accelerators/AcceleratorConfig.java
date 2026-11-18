package com.mintmachine.accelerators;

import net.neoforged.neoforge.common.ModConfigSpec;

/** config/botanypots_accelerators-common.toml */
final class AcceleratorConfig {
    private AcceleratorConfig() {}

    static final ModConfigSpec SPEC;
    static final ModConfigSpec.IntValue PERIOD;
    static final ModConfigSpec.IntValue CHUNK_RADIUS;
    static final ModConfigSpec.DoubleValue MAX_BONUS;
    static final ModConfigSpec.DoubleValue ACCELERATOR_BONUS;
    static final ModConfigSpec.DoubleValue AGRICARNATION_BONUS;
    static final ModConfigSpec.IntValue AGRICARNATION_MANA;
    static final ModConfigSpec.IntValue SOURCE_PER_STAGE;
    static final ModConfigSpec.IntValue STAGES;
    static final ModConfigSpec.IntValue SOURCE_PER_TREE;
    static final ModConfigSpec.BooleanValue DEBUG;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        PERIOD = b.comment("Ticks between checks (200 = 10 seconds, the same as Mystical Agriculture's default growth accelerator cooldown)")
                .defineInRange("period", 200, 20, 72000);
        CHUNK_RADIUS = b.comment("Chunks around each player that are checked (4 = a 9x9 chunk area)")
                .defineInRange("chunkRadius", 4, 0, 16);
        MAX_BONUS = b.comment("Cap on the total boost from accelerators and Agricarnations together (2.0 = +200%, i.e. 3x speed)")
                .defineInRange("maxBonus", 2.0, 0.0, 100.0);
        ACCELERATOR_BONUS = b.comment("Boost per Mystical Agriculture Growth Accelerator in reach (0.25 = +25%)")
                .defineInRange("acceleratorBonus", 0.25, 0.0, 100.0);
        AGRICARNATION_BONUS = b.comment("Boost per Botania Agricarnation covering the pot (0.25 = +25%)")
                .defineInRange("agricarnationBonus", 0.25, 0.0, 100.0);
        AGRICARNATION_MANA = b.comment("Mana an Agricarnation pays per boosted pot, every check")
                .defineInRange("agricarnationMana", 20, 0, 1000000);
        SOURCE_PER_STAGE = b.comment("Source per growth stage for Ars Nouveau Agronomic Sourcelinks (Ars's own amount for a crop)")
                .defineInRange("sourcePerStage", 20, 0, 100000);
        STAGES = b.comment("Growth stages a pot's crop counts as (wheat and Mystical Agriculture crops have 7)")
                .defineInRange("stages", 7, 1, 100);
        SOURCE_PER_TREE = b.comment("Source per tree grown from a sapling (Ars's own amount)")
                .defineInRange("sourcePerTree", 50, 0, 100000);
        DEBUG = b.comment("Log every boost and Source event")
                .define("debug", false);
        SPEC = b.build();
    }
}
