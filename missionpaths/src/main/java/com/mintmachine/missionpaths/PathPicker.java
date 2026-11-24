package com.mintmachine.missionpaths;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

/**
 * Chooses missions path by path. Knows nothing about Minecraft or the Missions mod: a sub-mission is an {@link Option}
 * (its type, the type's weight and "assign only once" flag, its path and its items).
 *
 * <p>Within one path it picks the way the Missions mod does over the whole pool: first a mission type, weighted by the
 * type's weight, then one of that type's sub-missions (in this path), then one of its items. On top of that:
 * <ul>
 *   <li>no two active missions share a type and item (they would share progress in the Missions mod);</li>
 *   <li>a type marked "assign only once" (fishing, diving, harvesting, trains...) is used once per week, unless its
 *       path has nothing else left;</li>
 *   <li>the missions of one path are of different types where the path allows it (not two kill missions on the
 *       Fishing row, say);</li>
 *   <li>if a path has nothing to offer, its slot is filled from any path, so there are always 10 missions.</li>
 * </ul>
 */
public final class PathPicker {

    /** One sub-mission. {@code sub} is whatever the caller needs to build the mission (the Missions mod's config). */
    public record Option(String type, boolean once, double typeWeight, String path, Object sub, List<String> items) {}

    /** A chosen mission: the sub-mission, the item and the path it belongs to. */
    public record Pick(Option option, String item) {
        public String type() { return option.type(); }
        public String path() { return option.path(); }
        public String key() { return key(option.type(), item); }
        static String key(String type, String item) { return type + '|' + item; }
    }

    private final List<Option> all;
    private final Map<String, List<Option>> byPath = new LinkedHashMap<>();
    private final Map<String, Boolean> onceByType = new HashMap<>();

    public PathPicker(Collection<Option> options) {
        this.all = List.copyOf(options);
        for (Option o : all) {
            byPath.computeIfAbsent(o.path(), p -> new ArrayList<>()).add(o);
            onceByType.merge(o.type(), o.once(), Boolean::logicalOr);
        }
    }

    public Set<String> paths() { return byPath.keySet(); }

    public int count(String path) { return byPath.getOrDefault(path, List.of()).size(); }

    public boolean isOnce(String type) { return onceByType.getOrDefault(type, false); }

    /**
     * The path of each of the {@code total} slots, in the order the missions are stored. The Missions screen shows
     * missions 1-5 in its left column and 6-10 in its right one, so handing the slots out round-robin (one of each path,
     * then the second of each...) puts each path on its own row when every path has 2.
     * Slots left over when the counts add up to less than {@code total} are {@code null}: any path.
     */
    public static List<String> layout(Map<String, Integer> slots, int total) {
        List<String> out = new ArrayList<>(total);
        int round = 0;
        boolean more = true;
        while (out.size() < total && more) {
            more = false;
            for (Map.Entry<String, Integer> e : slots.entrySet()) {
                int n = e.getValue() == null ? 0 : e.getValue();
                if (n > round) {
                    more = true;
                    if (out.size() < total) out.add(e.getKey());
                }
            }
            round++;
        }
        while (out.size() < total) out.add(null);
        return out;
    }

    /** A week of missions, one per slot of {@code layout}. Null only when there are no sub-missions at all. */
    public List<Pick> week(List<String> layout, Random rnd) {
        List<Pick> picks = new ArrayList<>(layout.size());
        Set<String> used = new HashSet<>();
        Set<String> onceUsed = new HashSet<>();
        Map<String, Set<String>> typesOnPath = new HashMap<>();
        for (String path : layout) {
            Pick p = pickWithFallback(path, used, onceUsed, typesOnPath.getOrDefault(path, Set.of()), rnd);
            if (p == null) return null;
            picks.add(p);
            used.add(p.key());
            if (p.option().once()) onceUsed.add(p.type());
            typesOnPath.computeIfAbsent(p.path(), k -> new HashSet<>()).add(p.type());
        }
        return picks;
    }

    /**
     * A new mission for slot {@code index} of {@code active} (type, item and path of every current mission), from
     * {@code path} (null: any path). It differs from every current mission, the one being replaced included.
     */
    public Pick reroll(List<String[]> active, int index, String path, Random rnd) {
        Set<String> used = new HashSet<>();
        Set<String> onceUsed = new HashSet<>();
        Set<String> typesOnPath = new HashSet<>();
        for (int i = 0; i < active.size(); i++) {
            String[] m = active.get(i);
            used.add(Pick.key(m[0], m[1]));
            if (i == index) continue;
            if (isOnce(m[0])) onceUsed.add(m[0]);
            if (path != null && m.length > 2 && path.equals(m[2])) typesOnPath.add(m[0]);
        }
        Pick p = pick(path, used, onceUsed, typesOnPath, rnd);
        if (p == null && path != null) p = pick(null, used, onceUsed, Set.of(), rnd);
        if (p == null) {   // everything is taken: allow the mission being replaced to come back
            used.remove(Pick.key(active.get(index)[0], active.get(index)[1]));
            p = pick(path, used, onceUsed, typesOnPath, rnd);
        }
        return p;
    }

    private Pick pickWithFallback(String path, Set<String> used, Set<String> onceUsed, Set<String> typesOnPath, Random rnd) {
        Pick p = pick(path, used, onceUsed, typesOnPath, rnd);
        if (p == null && path != null) p = pick(null, used, onceUsed, Set.of(), rnd);   // this path has nothing (left)
        if (p == null) p = pick(path, Set.of(), Set.of(), Set.of(), rnd);              // fewer sub-missions than slots
        if (p == null && path != null) p = pick(null, Set.of(), Set.of(), Set.of(), rnd);
        return p;
    }

    /**
     * One mission from {@code path} (null: any path) avoiding {@code used}; null if nothing is left. Types in
     * {@code onceUsed} (if "once") and in {@code avoidTypes} are skipped while the path offers anything else.
     */
    Pick pick(String path, Set<String> used, Set<String> onceUsed, Set<String> avoidTypes, Random rnd) {
        List<Option> pool = path == null ? all : byPath.getOrDefault(path, List.of());
        // type -> sub-missions of that type that still have a free item (sorted by type for repeatable tests)
        TreeMap<String, List<Option>> byType = new TreeMap<>();
        for (Option o : pool) {
            if (!o.items().isEmpty() && freeItems(o, used).size() > 0)
                byType.computeIfAbsent(o.type(), t -> new ArrayList<>()).add(o);
        }
        if (byType.isEmpty()) return null;

        List<String> types = new ArrayList<>();
        for (String t : byType.keySet()) if (!(isOnce(t) && onceUsed.contains(t)) && !avoidTypes.contains(t)) types.add(t);
        if (types.isEmpty())   // every type is already on this path: a second mission of a type is fine
            for (String t : byType.keySet()) if (!(isOnce(t) && onceUsed.contains(t))) types.add(t);
        if (types.isEmpty()) types.addAll(byType.keySet());   // only "once" types left in this path: allow a repeat

        String type = weighted(types, t -> byType.get(t).get(0).typeWeight(), rnd);
        List<Option> subs = byType.get(type);
        Option option = subs.get(rnd.nextInt(subs.size()));
        List<String> items = freeItems(option, used);
        return new Pick(option, items.get(rnd.nextInt(items.size())));
    }

    private static List<String> freeItems(Option o, Set<String> used) {
        List<String> free = new ArrayList<>(o.items().size());
        for (String item : o.items()) if (!used.contains(Pick.key(o.type(), item))) free.add(item);
        return free;
    }

    private static String weighted(List<String> types, java.util.function.ToDoubleFunction<String> weight, Random rnd) {
        double total = 0;
        for (String t : types) total += Math.max(0, weight.applyAsDouble(t));
        if (total <= 0) return types.get(rnd.nextInt(types.size()));
        double r = rnd.nextDouble() * total;
        for (String t : types) {
            r -= Math.max(0, weight.applyAsDouble(t));
            if (r < 0) return t;
        }
        return types.get(types.size() - 1);
    }
}
