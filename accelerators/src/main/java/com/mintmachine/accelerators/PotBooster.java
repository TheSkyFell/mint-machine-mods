package com.mintmachine.accelerators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.darkhax.botanypots.common.impl.block.entity.BotanyPotBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** The periodic check over pots near players. All of it runs on the server thread. */
final class PotBooster {
    private PotBooster() {}

    private static final String LOG_PREFIX = "[" + GrowthAccelerators.MOD_ID + "] ";

    /** Mystical Agriculture accelerator ranges (MysticalAgriculture 8.0.29); each reaches range + 2 blocks above it. */
    private static final Map<String, Integer> ACCELERATORS = Map.of(
            "mysticalagriculture:inferium_growth_accelerator", 9,
            "mysticalagriculture:prudentium_growth_accelerator", 18,
            "mysticalagriculture:tertium_growth_accelerator", 27,
            "mysticalagriculture:imperium_growth_accelerator", 36,
            "mysticalagriculture:supremium_growth_accelerator", 45,
            "mysticalagriculture:awakened_supremium_growth_accelerator", 54);

    private record PotKey(ResourceKey<Level> dimension, long pos) {}

    private static final class Bucket {
        final ServerLevel level;
        final Map<Long, BotanyPotBlockEntity> pots = new LinkedHashMap<>();
        final Map<Long, BlockEntity> flowers = new LinkedHashMap<>();

        Bucket(ServerLevel level) {
            this.level = level;
        }
    }

    private static Map<Block, Integer> reach = Map.of();
    private static int maxReach;
    private static boolean botania;
    private static boolean ars;

    private static final Map<PotKey, Float> lastGrowth = new HashMap<>();  // growth seen last time
    private static final Map<PotKey, Integer> lastStage = new HashMap<>(); // growth stage seen last time (for Source)
    private static boolean disabled;
    private static boolean potErrorLogged;

    static void onServerStarting(ServerStartingEvent event) {
        Map<Block, Integer> map = new IdentityHashMap<>();
        int max = 0;
        for (Map.Entry<String, Integer> e : ACCELERATORS.entrySet()) {
            ResourceLocation rl = ResourceLocation.parse(e.getKey());
            if (!BuiltInRegistries.BLOCK.containsKey(rl)) continue;
            int r = e.getValue() + 2;
            map.put(BuiltInRegistries.BLOCK.get(rl), r);
            max = Math.max(max, r);
        }
        reach = map;
        maxReach = max;
        botania = classExists(BotaniaCompat.CLASS);
        ars = classExists(ArsCompat.QUEUE_CLASS) && classExists(ArsCompat.LINK_CLASS);
        disabled = false;
        potErrorLogged = false;
        lastGrowth.clear();
        lastStage.clear();

        List<String> parts = new ArrayList<>();
        if (maxReach > 0) parts.add("Growth Accelerators");
        if (botania) parts.add("Agricarnations");
        if (ars) parts.add("Agronomic Sourcelinks");
        GrowthAccelerators.LOG.info(LOG_PREFIX + "Botany Pots work with {}",
                parts.isEmpty() ? "nothing (no supported mods found)" : String.join(", ", parts));
    }

    static void onServerStopped(ServerStoppedEvent event) {
        lastGrowth.clear();
        lastStage.clear();
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, PotBooster.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    static void onServerTick(ServerTickEvent.Post event) {
        if (disabled) return;
        MinecraftServer server = event.getServer();
        int period = AcceleratorConfig.PERIOD.get();
        if (server.getTickCount() % period != 0) return;
        try {
            // Gather pots and Agricarnations per level from chunks near players
            Map<ResourceKey<Level>, Bucket> levels = new LinkedHashMap<>();
            int radius = AcceleratorConfig.CHUNK_RADIUS.get();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerLevel level = player.serverLevel();
                Bucket bucket = levels.computeIfAbsent(level.dimension(), k -> new Bucket(level));
                int cx = player.blockPosition().getX() >> 4, cz = player.blockPosition().getZ() >> 4;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        LevelChunk chunk = level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                        if (chunk == null) continue;
                        for (BlockEntity be : chunk.getBlockEntities().values()) {
                            long key = be.getBlockPos().asLong();
                            if (be instanceof BotanyPotBlockEntity pot) bucket.pots.put(key, pot);
                            else if (botania && BotaniaCompat.isAgricarnation(be)) bucket.flowers.put(key, be);
                        }
                    }
                }
            }
            Set<PotKey> seen = new HashSet<>();
            for (Bucket b : levels.values()) {
                List<BlockEntity> flowers = new ArrayList<>(b.flowers.values());
                for (Map.Entry<Long, BotanyPotBlockEntity> e : b.pots.entrySet()) {
                    PotKey key = new PotKey(b.level.dimension(), e.getKey());
                    seen.add(key);
                    try {
                        handlePot(b.level, e.getValue(), flowers, key, period);
                    } catch (Exception ex) {
                        if (!potErrorLogged) {
                            potErrorLogged = true;
                            GrowthAccelerators.LOG.error(LOG_PREFIX + "Skipped a pot after an error (logged once): {} {}",
                                    key.dimension().location(), BlockPos.of(key.pos()), ex);
                        }
                    }
                }
            }
            // Forget pots that are no longer near anyone
            lastGrowth.keySet().retainAll(seen);
            lastStage.keySet().retainAll(seen);
        } catch (Exception | LinkageError e) {
            disabled = true;
            GrowthAccelerators.LOG.error(LOG_PREFIX + "Stopped after an error (it will not repeat until the server restarts)", e);
        }
    }

    private static int stageOf(double growth, double required, int stages) {
        return required > 0 ? (int) Math.max(0, Math.min(stages, Math.floor(stages * growth / required))) : 0;
    }

    private static int acceleratorsBelow(Level level, BlockPos pos) {
        if (maxReach == 0) return 0;
        int count = 0;
        int minY = level.getMinBuildHeight();
        for (int k = 1; k <= maxReach && pos.getY() - k >= minY; k++) {
            Integer r = reach.get(level.getBlockState(pos.below(k)).getBlock());
            if (r != null && k <= r) count++;
        }
        return count;
    }

    private static void handlePot(ServerLevel level, BotanyPotBlockEntity pot, List<BlockEntity> flowers, PotKey key, int period) {
        BlockPos pos = pot.getBlockPos();
        int required = pot.getRequiredGrowthTicks();
        float growth = pot.growthTime();
        Float before = lastGrowth.get(key);
        // Empty, or waxed (its growth is pinned at the maximum)
        if (required <= 0 || growth > required + 1e6) {
            lastGrowth.remove(key);
            lastStage.remove(key);
            return;
        }
        boolean debug = AcceleratorConfig.DEBUG.get();
        float g = growth;

        // ---- Boosts: only while the pot is actually growing ----
        if (before != null && growth != before && growth < required) {
            double maxBonus = AcceleratorConfig.MAX_BONUS.get();
            double flowerBonus = AcceleratorConfig.AGRICARNATION_BONUS.get();
            int mana = AcceleratorConfig.AGRICARNATION_MANA.get();
            int accel = acceleratorsBelow(level, pos);
            double bonus = Math.min(accel * AcceleratorConfig.ACCELERATOR_BONUS.get(), maxBonus);
            int paid = 0;
            if (bonus < maxBonus && !flowers.isEmpty() && flowerBonus > 0) {
                int need = (int) Math.ceil((maxBonus - bonus) / flowerBonus - 1e-9);
                for (BlockEntity f : flowers) {
                    if (paid >= need) break;
                    if (!BotaniaCompat.canHelp(f, pos, mana)) continue;
                    BotaniaCompat.pay(f, mana);
                    paid++;
                }
                bonus = Math.min(bonus + paid * flowerBonus, maxBonus);
            }
            if (bonus > 0) {
                g = (float) Math.min(growth + bonus * period, required);
                pot.updateGrowthTime(g);
                if (debug) {
                    GrowthAccelerators.LOG.info(LOG_PREFIX + "{}: {} accelerator(s), {} agricarnation(s), growth {} -> {} of {}",
                            pos, accel, paid, Math.round(growth), Math.round(g), required);
                }
            }
        }

        // ---- Source for Agronomic Sourcelinks, from the stages grown since last time ----
        int stagesTotal = AcceleratorConfig.STAGES.get();
        int stage = stageOf(g, required, stagesTotal);
        if (ars && before != null) {
            Integer last = lastStage.get(key);
            int prev = last == null ? stage : last;
            int stages, trees = 0;
            if (g < before) { // harvested since last time (and maybe regrowing)
                if (prev < stagesTotal) trees++;
                stages = (stagesTotal - prev) + stage;
            } else {
                stages = Math.max(0, stage - prev);
                if (stage == stagesTotal && prev < stagesTotal) trees++;
            }
            if (pot.getSeedItem().is(ItemTags.SAPLINGS)) {
                int amount = AcceleratorConfig.SOURCE_PER_TREE.get();
                for (int i = 0; i < trees; i++) ArsCompat.addSource(level, amount, pos);
                if (debug && trees > 0) GrowthAccelerators.LOG.info(LOG_PREFIX + "{}: {} tree(s) grown -> Source event", pos, trees);
            } else {
                int amount = AcceleratorConfig.SOURCE_PER_STAGE.get();
                for (int i = 0; i < stages; i++) ArsCompat.addSource(level, amount, pos);
                if (debug && stages > 0) GrowthAccelerators.LOG.info(LOG_PREFIX + "{}: {} stage(s) grown -> Source events", pos, stages);
            }
        }
        lastGrowth.put(key, g);
        lastStage.put(key, stage);
    }
}
