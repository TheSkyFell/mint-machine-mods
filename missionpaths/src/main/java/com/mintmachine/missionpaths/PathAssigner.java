package com.mintmachine.missionpaths;

import com.kryeit.missions.MissionType;
import com.kryeit.missions.config.ConfigReader.MissionTypeConfig;
import com.kryeit.missions.config.ConfigReader.Reward;
import com.kryeit.missions.config.ConfigReader.SubMissionConfig;
import com.kryeit.missions.config.Range;
import com.mojang.logging.LogUtils;
import com.mintmachine.missionpaths.PathPicker.Option;
import com.mintmachine.missionpaths.PathPicker.Pick;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.slf4j.Logger;

/**
 * Hands out missions path by path, in place of the Missions mod's DataStorage.reassignActiveMissions (the weekly
 * refresh) and reassignActiveMission (a reroll). Builds exactly the tags the Missions mod builds, plus a "path" entry.
 * Any problem returns null, and the Missions mod then picks as it always did.
 */
public final class PathAssigner {
    private static final Logger LOG = LogUtils.getLogger();
    /** The Missions mod always shows 10 missions. */
    public static final int MISSIONS = 10;
    /** Where the Missions mod reads its config (relative to the game directory, as it does). */
    static Path configDir = Path.of("config", "missions");
    static Random random = new Random();

    private static PathConfig config;
    private static long configStamp = Long.MIN_VALUE;
    private static PathIndex index;
    private static long indexStamp = Long.MIN_VALUE;
    private static final Set<String> reported = ConcurrentHashMap.newKeySet();

    private PathAssigner() {}

    // ---------------------------------------------------------------- the two replaced methods

    /** Ten new missions, or null to let the Missions mod pick. */
    public static List<CompoundTag> week(Map<MissionType, MissionTypeConfig> missions) {
        try {
            PathConfig cfg = config();
            if (!cfg.enabled) return null;
            List<Pick> picks = picker(missions, cfg, index()).week(PathPicker.layout(cfg.slots, MISSIONS), random);
            if (picks == null) return null;
            List<CompoundTag> tags = new ArrayList<>(picks.size());
            for (Pick p : picks) tags.add(tag(p));
            return tags;
        } catch (Throwable t) {
            report("handing out the week's missions", t);
            return null;
        }
    }

    /** The mission that replaces slot {@code index} of {@code active}, or null to let the Missions mod pick. */
    public static CompoundTag reroll(Map<MissionType, MissionTypeConfig> missions, ListTag active, int index) {
        try {
            PathConfig cfg = config();
            if (!cfg.enabled || active == null || index < 0 || index >= active.size()) return null;
            PathIndex idx = index();
            List<String[]> current = new ArrayList<>(active.size());
            for (int i = 0; i < active.size(); i++) {
                CompoundTag t = active.getCompound(i);
                current.add(new String[] {t.getString("mission_id"), t.getString("item"), slotPath(t, cfg, idx)});
            }
            String path = current.get(index)[2];
            Pick p = picker(missions, cfg, idx).reroll(current, index, path, random);
            return p == null ? null : tag(p);
        } catch (Throwable t) {
            report("rerolling a mission", t);
            return null;
        }
    }

    // ---------------------------------------------------------------- building blocks

    static PathPicker picker(Map<MissionType, MissionTypeConfig> missions, PathConfig cfg, PathIndex idx) {
        List<Option> options = new ArrayList<>();
        for (MissionTypeConfig tc : missions.values()) {
            if (tc == null || tc.missionType() == null || tc.subMissions() == null) continue;
            MissionType type = tc.missionType();
            String id = type.id();
            boolean once = type.assignOnlyOnce();
            for (SubMissionConfig sub : tc.subMissions().values()) {
                // the Missions mod would fail on a sub-mission without items, titles or rewards: leave those out
                if (sub == null || isEmpty(sub.items()) || sub.titles() == null || sub.titles().isEmpty()
                        || sub.rewards() == null || sub.rewards().isEmpty()) continue;
                Map<String, int[]> items = new TreeMap<>();
                sub.items().forEach((item, r) -> items.put(item, new int[] {r.lower(), r.upper()}));
                String path = idx.pathOf(id, items);
                if (path == null) path = cfg.pathOfType(id);
                options.add(new Option(id, once, tc.weight(), path, sub, List.copyOf(items.keySet())));
            }
        }
        return new PathPicker(options);
    }

    /** The path of a current mission: written on it, or (for missions handed out before this add-on) looked up. */
    static String slotPath(CompoundTag mission, PathConfig cfg, PathIndex idx) {
        String path = mission.getString("path");
        if (!path.isEmpty()) return path;
        String type = mission.getString("mission_id");
        path = idx.pathOfItem(type, mission.getString("item"));
        return path != null ? path : cfg.pathOfType(type);
    }

    /** The Missions mod's mission tag (DataStorage.createActiveMissionTag in 0.5.2) for this pick, plus its path. */
    static CompoundTag tag(Pick p) {
        SubMissionConfig sub = (SubMissionConfig) p.option().sub();
        Range amount = sub.items().get(p.item());
        Reward reward = sub.rewards().get(random.nextInt(sub.rewards().size()));
        CompoundTag tag = new CompoundTag();
        tag.putString("item", p.item());
        tag.putBoolean("completed", false);
        tag.putString("mission_id", p.type());
        tag.putInt("required_amount", amount.getRandomValue());
        tag.putString("title", sub.titles().get(random.nextInt(sub.titles().size())));
        tag.putString("reward_item", reward.item());
        tag.putInt("reward_min", reward.count().lower());
        tag.putInt("reward_max", reward.count().upper());
        tag.putString("path", p.path());
        return tag;
    }

    private static boolean isEmpty(Map<?, ?> m) { return m == null || m.isEmpty(); }

    // ---------------------------------------------------------------- config files

    static synchronized PathConfig config() {
        Path file = configDir.resolve(PathConfig.FILE_NAME);
        long stamp = stamp(file);
        if (config == null || stamp != configStamp) {
            config = PathConfig.load(configDir, LOG::warn);
            configStamp = stamp(file);
        }
        return config;
    }

    static synchronized PathIndex index() {
        Path file = configDir.resolve("missions.json");
        long stamp = stamp(file);
        if (index == null || stamp != indexStamp) {
            try {
                index = PathIndex.read(file);
                for (String w : index.warnings) LOG.warn("[missions_paths] {}", w);
            } catch (Exception e) {
                LOG.warn("[missions_paths] Couldn't read the paths in {} ({}); every mission type uses its path from {}",
                        file, e.toString(), PathConfig.FILE_NAME);
                index = PathIndex.empty();
            }
            indexStamp = stamp;
        }
        return index;
    }

    private static long stamp(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis() ^ Files.size(file);
        } catch (Exception e) {
            return -1;
        }
    }

    /** Logs a problem once (with its stack trace), then quietly: the Missions mod's own picking takes over each time. */
    private static void report(String what, Throwable t) {
        String key = what + ':' + t;
        if (reported.add(key)) {
            LOG.error("[missions_paths] Failed {}; the Missions mod picks instead", what, t);
        } else {
            LOG.debug("[missions_paths] Failed {} again: {}", what, t.toString());
        }
    }

    // ---------------------------------------------------------------- startup summary

    /** One log line on the paths, and warnings for paths that can't be filled or never get a slot. */
    static void logSummary(Map<MissionType, MissionTypeConfig> missions) {
        try {
            PathConfig cfg = config();
            if (!cfg.enabled) {
                LOG.info("[missions_paths] Disabled in {}: the Missions mod picks missions itself", PathConfig.FILE_NAME);
                return;
            }
            PathIndex idx = index();
            PathPicker picker = picker(missions, cfg, idx);
            StringBuilder slots = new StringBuilder();
            cfg.slots.forEach((p, n) -> slots.append(slots.length() == 0 ? "" : ", ").append(p).append(' ').append(n));
            StringBuilder pool = new StringBuilder();
            java.util.LinkedHashSet<String> order = new java.util.LinkedHashSet<>(cfg.slots.keySet());
            order.addAll(new java.util.TreeSet<>(picker.paths()));
            for (String p : order) pool.append(pool.length() == 0 ? "" : ", ").append(p).append(' ').append(picker.count(p));
            LOG.info("[missions_paths] Weekly missions by path: {} (sub-missions: {})", slots, pool);
            if (cfg.totalSlots() != MISSIONS)
                LOG.warn("[missions_paths] The slots in {} add up to {}, not {}: {}", PathConfig.FILE_NAME, cfg.totalSlots(),
                        MISSIONS, cfg.totalSlots() < MISSIONS ? "the rest come from any path" : "only the first 10 are used");
            for (Map.Entry<String, Integer> e : cfg.slots.entrySet())
                if (e.getValue() > 0 && picker.count(e.getKey()) == 0)
                    LOG.warn("[missions_paths] Path '{}' has {} slots but no sub-missions; its slots come from the other paths",
                            e.getKey(), e.getValue());
            for (String p : picker.paths())
                if (cfg.slots.getOrDefault(p, 0) <= 0)
                    LOG.warn("[missions_paths] {} sub-missions are on path '{}', which has no slots in {}; they're only used "
                            + "when another path runs out", picker.count(p), p, PathConfig.FILE_NAME);
            if (idx.untagged > 0)
                LOG.info("[missions_paths] {} sub-missions in missions.json have no \"path\" and use their type's path",
                        idx.untagged);
        } catch (Throwable t) {
            report("checking the paths", t);
        }
    }
}
