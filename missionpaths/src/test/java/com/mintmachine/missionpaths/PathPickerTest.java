package com.mintmachine.missionpaths;

import com.mintmachine.missionpaths.PathPicker.Option;
import com.mintmachine.missionpaths.PathPicker.Pick;
import java.util.*;

import org.junit.jupiter.api.Test;

/** Checks the picking rules on made-up sub-missions. No Minecraft needed. */
class PathPickerTest {
    static int checks;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    static Option opt(String type, boolean once, double w, String path, String... items) {
        return new Option(type, once, w, path, type + "/" + path + "/" + items[0], List.of(items));
    }

    static LinkedHashMap<String, Integer> slots(Object... kv) {
        LinkedHashMap<String, Integer> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Integer) kv[i + 1]);
        return m;
    }

    @Test
    void picksTwoPerPathAndRerollsInPath() {
        // ---- layout
        List<String> l = PathPicker.layout(slots("create", 2, "travel", 2, "magic", 2, "farming", 2, "fishing", 2), 10);
        check(l.equals(List.of("create", "travel", "magic", "farming", "fishing", "create", "travel", "magic", "farming", "fishing")),
                "round-robin layout puts each path on one row: " + l);
        check(PathPicker.layout(slots("a", 3, "b", 1), 10).equals(Arrays.asList("a", "b", "a", "a", null, null, null, null, null, null)),
                "short layout is padded with 'any'");
        check(PathPicker.layout(slots("a", 8, "b", 8), 10).size() == 10, "long layout is cut to 10");
        check(PathPicker.layout(slots("a", 0, "b", 10), 10).equals(Collections.nCopies(10, "b")), "zero-slot path skipped");

        // ---- a pool shaped like the pack's
        List<Option> pool = new ArrayList<>();
        pool.add(opt("craft", false, 1, "create", "shaft", "cogwheel", "gearbox"));
        pool.add(opt("break", false, 1, "create", "stone", "granite"));
        pool.add(opt("drill", true, 1, "create", "stone", "andesite"));
        pool.add(opt("press", false, 0.6, "create", "iron_sheet"));
        pool.add(opt("walk", false, 1, "travel", "missions:statistic"));
        pool.add(opt("dive", true, 1, "travel", "water"));
        pool.add(opt("kill", false, 1, "travel", "zombie", "guardian"));
        pool.add(opt("mix", false, 1, "magic", "starmetal_ingot"));
        pool.add(opt("crush", false, 1, "magic", "arcane_crystal", "rune"));
        pool.add(opt("harvester", true, 1, "magic", "inferium_seeds"));
        pool.add(opt("harvester", true, 1, "farming", "wheat_seeds", "carrot"));
        pool.add(opt("eat", false, 1, "farming", "apple", "bread"));
        pool.add(opt("feed", false, 1, "farming", "wheat"));
        pool.add(opt("fish", true, 3, "fishing", "cod", "salmon"));
        pool.add(opt("fish", true, 3, "fishing", "driftfin"));
        pool.add(opt("kill", false, 1, "fishing", "guardian", "drowned"));
        pool.add(opt("eat", false, 1, "fishing", "cooked_cod"));
        PathPicker picker = new PathPicker(pool);
        List<String> layout = PathPicker.layout(slots("create", 2, "travel", 2, "magic", 2, "farming", 2, "fishing", 2), 10);

        Random rnd = new Random(42);
        Map<String, Integer> fishWeeks = new HashMap<>();
        int weeks = 20000, harvesterTwice = 0, onceRepeats = 0;
        Map<String, Map<String, Integer>> typeCount = new TreeMap<>();
        for (int w = 0; w < weeks; w++) {
            List<Pick> week = picker.week(layout, rnd);
            check(week != null && week.size() == 10, "always 10 missions");
            Map<String, Integer> perPath = new HashMap<>();
            Set<String> keys = new HashSet<>();
            Map<String, Integer> onceTypes = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                Pick p = week.get(i);
                check(p.path().equals(layout.get(i)), "slot " + i + " holds its own path");
                perPath.merge(p.path(), 1, Integer::sum);
                check(keys.add(p.key()), "no repeated type+item: " + p.key());
                check(p.option().items().contains(p.item()), "item comes from its sub-mission");
                if (p.option().once()) onceTypes.merge(p.type(), 1, Integer::sum);
                typeCount.computeIfAbsent(p.path(), k -> new TreeMap<>()).merge(p.type(), 1, Integer::sum);
            }
            for (String path : List.of("create", "travel", "magic", "farming", "fishing"))
                check(perPath.getOrDefault(path, 0) == 2, "2 missions on " + path + ", got " + perPath);
            for (int i = 0; i < 5; i++)
                check(!week.get(i).type().equals(week.get(i + 5).type()), "a path's two missions differ in type: " + week.get(i).path());
            if (onceTypes.getOrDefault("harvester", 0) > 1) harvesterTwice++;
            for (Map.Entry<String, Integer> e : onceTypes.entrySet()) if (e.getValue() > 1 && !e.getKey().equals("harvester")) onceRepeats++;
            fishWeeks.merge(onceTypes.containsKey("fish") ? "with fish" : "without", 1, Integer::sum);
        }
        // magic has 3 types of which only harvester is "once", so harvester never needs to repeat; same for the others
        check(harvesterTwice == 0, "harvester (once) used twice in " + harvesterTwice + " weeks");
        check(onceRepeats == 0, "a once-only type repeated in " + onceRepeats + " weeks");
        System.out.println("  20000 weeks: every week 2 per path, no repeats; per path, picks by type: " + typeCount);
        System.out.println("  fishing weeks with a catch mission: " + fishWeeks);

        // ---- weights: within 'create', press (0.6) should come up about 0.6/3.6 of the time
        double pressShare = typeCount.get("create").getOrDefault("press", 0) / (double) (2 * weeks);
        check(pressShare > 0.12 && pressShare < 0.22, "press share " + pressShare);

        // ---- rerolls stay in the slot's path and change the mission
        for (int r = 0; r < 5000; r++) {
            List<Pick> week = picker.week(layout, rnd);
            List<String[]> active = new ArrayList<>();
            for (Pick p : week) active.add(new String[] {p.type(), p.item(), p.path()});
            int idx = rnd.nextInt(10);
            Pick n = picker.reroll(active, idx, week.get(idx).path(), rnd);
            check(n.path().equals(week.get(idx).path()), "reroll keeps the path");
            int partner = idx < 5 ? idx + 5 : idx - 5;
            if (n.type().equals(week.get(partner).type())) {
                // allowed only when the path had nothing of another type left to give
                Set<String> taken = new HashSet<>(), onceTaken = new HashSet<>();
                for (int i = 0; i < 10; i++) {
                    taken.add(active.get(i)[0] + "|" + active.get(i)[1]);
                    if (i != idx && picker.isOnce(active.get(i)[0])) onceTaken.add(active.get(i)[0]);
                }
                for (Option o : pool)
                    if (o.path().equals(n.path()) && !o.type().equals(n.type()) && !(o.once() && onceTaken.contains(o.type())))
                        for (String it : o.items())
                            check(taken.contains(o.type() + "|" + it), "reroll repeated the type " + n.type() + " though " + o.type() + " " + it + " was free");
            }
            Set<String> others = new HashSet<>();
            for (String[] m : active) others.add(m[0] + "|" + m[1]);
            check(!others.contains(n.key()), "reroll gives a mission not already active (old one included): " + n.key());
        }

        // ---- a path with a single mission: rerolling it falls back to any path rather than repeating
        List<Option> tiny = new ArrayList<>(pool);
        tiny.removeIf(o -> o.path().equals("magic"));
        tiny.add(opt("mix", false, 1, "magic", "only_one"));
        PathPicker tp = new PathPicker(tiny);
        List<Pick> week = tp.week(layout, rnd);
        long magic = week.stream().filter(p -> p.path().equals("magic")).count();
        check(magic == 1, "only one magic mission exists, so one magic slot: " + magic);
        check(week.size() == 10, "the other magic slot is filled from another path");

        // ---- an empty path: its slots come from the others; still 10, no repeats
        List<Option> noFish = new ArrayList<>(pool);
        noFish.removeIf(o -> o.path().equals("fishing"));
        week = new PathPicker(noFish).week(layout, rnd);
        check(week.size() == 10 && week.stream().map(Pick::key).distinct().count() == 10, "no fishing: 10 distinct missions");

        // ---- an unknown path in the layout ('any' too)
        week = picker.week(PathPicker.layout(slots("create", 2, "nonsense", 3), 10), rnd);
        check(week.size() == 10, "unknown path and padding still give 10");

        // ---- only once-types left in a path: they may repeat rather than leave the slot to another path
        List<Option> onceOnly = List.of(opt("dive", true, 1, "travel", "water", "lava"), opt("craft", false, 1, "create", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j"));
        week = new PathPicker(onceOnly).week(PathPicker.layout(slots("travel", 2, "create", 8), 10), rnd);
        check(week.stream().filter(p -> p.type().equals("dive")).count() == 2, "both travel slots are dives (water and lava)");

        // ---- nothing at all
        check(new PathPicker(List.of()).week(layout, rnd) == null, "no sub-missions: null (Missions mod picks)");

        System.out.println("PathPickerTest OK (" + checks + " checks)");
    }
}
