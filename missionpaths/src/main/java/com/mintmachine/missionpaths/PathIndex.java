package com.mintmachine.missionpaths;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The "path" of each sub-mission, read from the Missions mod's own missions.json. The Missions mod ignores fields it
 * doesn't know, so the path sits on the sub-mission itself. Its parsed config keeps only titles, items and rewards, so a
 * sub-mission is matched back to its path by its type and items (with their amounts), which are unique within a type.
 */
public final class PathIndex {
    /** type -> item signature -> path */
    private final Map<String, Map<String, String>> byItems = new HashMap<>();
    /** type|item -> paths of the sub-missions that hold that item (to place missions handed out before this add-on) */
    private final Map<String, Set<String>> byItem = new HashMap<>();
    final List<String> warnings = new ArrayList<>();
    final int untagged;

    private PathIndex(JsonObject root) {
        int noPath = 0;
        for (Map.Entry<String, JsonElement> t : root.entrySet()) {
            if (!t.getValue().isJsonObject()) continue;   // "config-version"
            JsonObject type = t.getValue().getAsJsonObject();
            if (!type.has("missions") || !type.get("missions").isJsonArray()) continue;
            for (JsonElement m : type.getAsJsonArray("missions")) {
                if (!m.isJsonObject()) continue;
                JsonObject sub = m.getAsJsonObject();
                if (!sub.has("path") || !sub.has("items")) { noPath++; continue; }
                String path = sub.get("path").getAsString().trim();
                if (path.isEmpty()) { noPath++; continue; }
                Map<String, int[]> items = new TreeMap<>();
                for (Map.Entry<String, JsonElement> it : sub.getAsJsonObject("items").entrySet())
                    items.put(it.getKey(), range(it.getValue().getAsString()));
                String sig = signature(items);
                String prev = byItems.computeIfAbsent(t.getKey(), k -> new HashMap<>()).putIfAbsent(sig, path);
                if (prev != null && !prev.equals(path))
                    warnings.add("two '" + t.getKey() + "' sub-missions have the same items but different paths ("
                            + prev + ", " + path + "); both count as " + prev);
                for (String item : items.keySet())
                    byItem.computeIfAbsent(t.getKey() + '|' + item, k -> new LinkedHashSet<>()).add(path);
            }
        }
        this.untagged = noPath;
    }

    static PathIndex empty() { return new PathIndex(new JsonObject()); }

    static PathIndex read(Path missionsJson) throws Exception {
        return new PathIndex(JsonParser.parseString(Files.readString(missionsJson, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    /** The path written on the sub-mission of {@code type} with these items and amounts, or null. */
    public String pathOf(String type, Map<String, int[]> items) {
        Map<String, String> m = byItems.get(type);
        return m == null ? null : m.get(signature(new TreeMap<>(items)));
    }

    /** The path of the sub-missions of {@code type} holding {@code item}, if they all agree; otherwise null. */
    public String pathOfItem(String type, String item) {
        Set<String> paths = byItem.get(type + '|' + item);
        return paths != null && paths.size() == 1 ? paths.iterator().next() : null;
    }

    /** Same parsing as the Missions mod's Range.fromString: "lo-hi", either order. */
    static int[] range(String s) {
        String[] split = s.split("-");
        int a = Integer.parseInt(split[0].trim()), b = Integer.parseInt(split[1].trim());
        return new int[] {Math.min(a, b), Math.max(a, b)};
    }

    static String signature(Map<String, int[]> sortedItems) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, int[]> e : sortedItems.entrySet()) {
            int[] r = e.getValue();
            sb.append(e.getKey()).append('=').append(r[0]).append('-').append(r[1]).append(';');
        }
        return sb.toString();
    }
}
