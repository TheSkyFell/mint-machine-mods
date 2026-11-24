package com.mintmachine.missionpaths;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** config/missions/paths.json: how many of the 10 weekly missions each path gets, and the paths of whole types. */
public final class PathConfig {
    public static final String FILE_NAME = "paths.json";

    public final boolean enabled;
    /** path -> slots, in the order the paths appear on the missions screen */
    public final LinkedHashMap<String, Integer> slots;
    /** mission type -> path, for sub-missions in missions.json without a "path" */
    public final Map<String, String> typePaths;
    public final String defaultPath;

    PathConfig(boolean enabled, LinkedHashMap<String, Integer> slots, Map<String, String> typePaths, String defaultPath) {
        this.enabled = enabled;
        this.slots = slots;
        this.typePaths = typePaths;
        this.defaultPath = defaultPath;
    }

    public String pathOfType(String type) {
        return typePaths.getOrDefault(type, defaultPath);
    }

    public int totalSlots() {
        int n = 0;
        for (int v : slots.values()) n += Math.max(0, v);
        return n;
    }

    // ---------------------------------------------------------------- defaults

    static PathConfig defaults() {
        LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();
        for (String p : new String[] {"create", "travel", "magic", "farming", "fishing"}) slots.put(p, 2);
        Map<String, String> types = new LinkedHashMap<>();
        for (String t : new String[] {"place", "break", "saw", "drill", "craft", "mix", "compact", "press", "crush", "mill", "cut"})
            types.put(t, "create");
        for (String t : new String[] {"kill", "dive", "ride", "sail", "fly", "walk", "swim", "minecart", "train-driver",
                "train-passenger", "train-driver-passenger", "relocate", "belt-walk", "chain-ride"})
            types.put(t, "travel");
        for (String t : new String[] {"eat", "feed", "harvester"}) types.put(t, "farming");
        types.put("fish", "fishing");
        return new PathConfig(true, slots, types, "create");
    }

    private static final String README =
            "Missions Paths. Each week the Missions mod hands out 10 missions; this add-on splits them between paths. "
            + "'slots' is how many missions each path gets every week (they should add up to 10). Rerolling a mission "
            + "gives another one from the same path. A sub-mission's path is the \"path\" written on it in missions.json; "
            + "sub-missions without one use 'type_paths' (by mission type), then 'default_path'. A path with nothing to "
            + "offer has its slots filled from the other paths. Set 'enabled' to false to go back to the Missions mod's own "
            + "picking. Changes here count from the next missions handed out; changes to missions.json need a restart, as "
            + "the Missions mod reads it once.";

    static JsonObject toJson(PathConfig c) {
        JsonObject o = new JsonObject();
        o.addProperty("_readme", README);
        o.addProperty("enabled", c.enabled);
        JsonObject s = new JsonObject();
        c.slots.forEach(s::addProperty);
        o.add("slots", s);
        o.addProperty("default_path", c.defaultPath);
        JsonObject t = new JsonObject();
        c.typePaths.forEach(t::addProperty);
        o.add("type_paths", t);
        return o;
    }

    // ---------------------------------------------------------------- reading

    /** Reads {@code dir/paths.json}, writing the defaults there first if it doesn't exist. Never throws. */
    static PathConfig load(Path dir, java.util.function.Consumer<String> warn) {
        Path file = dir.resolve(FILE_NAME);
        PathConfig def = defaults();
        if (!Files.exists(file)) {
            try {
                Files.createDirectories(dir);
                Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
                Files.writeString(file, gson.toJson(toJson(def)) + "\n", StandardCharsets.UTF_8);
            } catch (IOException e) {
                warn.accept("Couldn't write " + file + " (" + e + "); using the defaults");
            }
            return def;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
            LinkedHashMap<String, Integer> slots = new LinkedHashMap<>();
            if (o.has("slots")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("slots").entrySet())
                    slots.put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
            } else {
                slots.putAll(def.slots);
            }
            Map<String, String> types = new LinkedHashMap<>(def.typePaths);
            if (o.has("type_paths")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("type_paths").entrySet())
                    types.put(e.getKey(), e.getValue().getAsString());
            }
            String defaultPath = o.has("default_path") ? o.get("default_path").getAsString() : def.defaultPath;
            return new PathConfig(enabled, slots, types, defaultPath);
        } catch (Exception e) {
            warn.accept("Couldn't read " + file + " (" + e + "); using the defaults until it's fixed");
            return def;
        }
    }
}
