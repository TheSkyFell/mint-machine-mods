package com.mintmachine.accelerators;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Botany Pots join the other growing machinery. Botany Pots grow on their own timer, so nothing that works on crops
 * in the world (random ticks, bone meal, crop-growth events) ever reaches them. This bridges three:
 * <ol>
 * <li>Mystical Agriculture Growth Accelerators below a pot, within the tier's reach (range + 2 blocks, as in the
 * world; a pot may also sit right on top of one): +25% growth each.</li>
 * <li>Botania Agricarnations whose area covers the pot (11x11, from 1 block below the flower to 4 above; the small
 * one 5x5): +25% growth each, paid with 20 mana per pot every 10 seconds from the flower's own mana. A flower with
 * a redstone signal, or without enough mana, doesn't help. Boosts from 1 and 2 share one cap: +200% (three times
 * as fast).</li>
 * <li>Ars Nouveau Agronomic Sourcelinks within 15 blocks get Source as a pot's crop grows, through Ars's own event
 * queue, exactly as for a crop in the world: 20 Source per growth stage (a pot's growth counts as 7 stages), or 50
 * for each tree grown from a sapling.</li>
 * </ol>
 * Every 10 seconds it looks at the pots in chunks near players. Pots that are empty, waxed, full, or not growing
 * (no light, missing water, ...) get no boost. The numbers are in config/botanypots_accelerators-common.toml.
 */
@Mod(GrowthAccelerators.MOD_ID)
public final class GrowthAccelerators {
    public static final String MOD_ID = "botanypots_accelerators";
    static final Logger LOG = LogUtils.getLogger();

    public GrowthAccelerators(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, AcceleratorConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(PotBooster::onServerStarting);
        NeoForge.EVENT_BUS.addListener(PotBooster::onServerStopped);
        NeoForge.EVENT_BUS.addListener(PotBooster::onServerTick);
    }
}
