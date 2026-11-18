package com.mintmachine.combat;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;

/**
 * config/mintmachine_combat.json. Written with the defaults below the first time the game starts; edit it and
 * restart to change the numbers. Netherite is the benchmark (sword 8, axe 10, armor 20 / toughness 12); endgame
 * gear sits modestly above it and its abilities and set bonuses do the rest. Cataclysm's boss gear is left as it is.
 * <ul>
 * <li>Mystical Agriculture swords: Inferium 6, Prudentium 7, Tertium 8, Imperium 9, Supremium 10, Awakened
 * Supremium 11. Scythes and sickles +1, axes +2 (at least 9), pickaxes -2, shovels -1.5, hoes -4 (at least 1).
 * Supremium tools stay unbreakable.</li>
 * <li>Mystical Agriculture armor (set armor / toughness): Inferium 15/0, Prudentium 18/2, Tertium 20/6, Imperium
 * 20/10, Supremium 20/12, Awakened Supremium 22/14.</li>
 * <li>Forbidden &amp; Arcanus: Tyr 22/12, Draco Arcanus 21/10. Ars Elemental heavy sets and Ars Technica's
 * Machinaguard: 20/12 (their mana bonuses are unchanged).</li>
 * <li>HerbalBrews' jug: no attack damage (it hit harder than a Netherite sword).</li>
 * <li>Create: Ironworks armor as a ladder: bronze 15/0 and brass 16/0 (unchanged), rose quartz 17/0, sturdy 18/4,
 * steel 20/6 with knockback resistance 0.1 per piece like Netherite.</li>
 * <li>Protection Pixel Linkplate: 15/8 (was 15/16, more toughness than Netherite).</li>
 * <li>Create Stuff &amp; Netherite Additions' netherite exoskeleton: 10 armor, 4 toughness, 0.15 knockback
 * resistance, unbreakable. Its netherite jetpack: unbreakable, armor unchanged.</li>
 * <li>Create Stuff 'N Additions: zinc axe 7 and rose quartz axe 8 damage.</li>
 * </ul>
 */
final class CombatConfig {
    static final int UNBREAKABLE = -1;
    private static final String FILE = CombatRebalance.MOD_ID + ".json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /**
     * One item's settings; null means "leave unchanged".
     *
     * @param damage attack damage as the tooltip shows it (the player's base 1 plus the item's bonus)
     * @param durability maximum durability, or {@link #UNBREAKABLE}
     * @param noDamage true removes the item's attack damage and attack speed altogether
     */
    record ItemSettings(Double damage, Double armor, Double toughness, Double knockbackResistance,
                        Integer durability, boolean noDamage) {
        boolean changesAttributes() {
            return damage != null || armor != null || toughness != null || knockbackResistance != null || noDamage;
        }

        ItemSettings withDamage(double value) {
            return new ItemSettings(value, armor, toughness, knockbackResistance, durability, noDamage);
        }

        ItemSettings withArmor(double a, double t, Double k) {
            return new ItemSettings(damage, a, t, k, durability, noDamage);
        }

        ItemSettings withDurability(int value) {
            return new ItemSettings(damage, armor, toughness, knockbackResistance, value, noDamage);
        }

        ItemSettings withNoDamage() {
            return new ItemSettings(damage, armor, toughness, knockbackResistance, durability, true);
        }
    }

    private static final ItemSettings NONE = new ItemSettings(null, null, null, null, null, false);

    private final Map<String, ItemSettings> items;

    private CombatConfig(Map<String, ItemSettings> items) {
        this.items = items;
    }

    Map<String, ItemSettings> items() {
        return items;
    }

    static CombatConfig load() {
        Path path = FMLPaths.CONFIGDIR.get().resolve(FILE);
        JsonObject json;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            } catch (Exception e) {
                CombatRebalance.LOG.error("[{}] Could not read config/{}; using the defaults this time: {}",
                        CombatRebalance.MOD_ID, FILE, e.toString());
                json = defaults();
            }
        } else {
            json = defaults();
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(json, writer);
                }
            } catch (IOException e) {
                CombatRebalance.LOG.warn("[{}] Could not write the default config/{}: {}", CombatRebalance.MOD_ID, FILE, e.toString());
            }
        }
        return new CombatConfig(parse(json));
    }

    private static Map<String, ItemSettings> parse(JsonObject json) {
        Map<String, ItemSettings> items = new LinkedHashMap<>();
        JsonObject damage = object(json, "damage");
        if (damage != null) {
            damage.entrySet().forEach(e -> {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()) {
                    items.merge(e.getKey(), NONE.withDamage(e.getValue().getAsDouble()),
                            (old, add) -> old.withDamage(add.damage()));
                } else warn("damage", e.getKey());
            });
        }
        JsonObject armor = object(json, "armor");
        if (armor != null) {
            armor.entrySet().forEach(e -> {
                JsonElement v = e.getValue();
                if (v.isJsonArray() && v.getAsJsonArray().size() >= 2) {
                    JsonArray a = v.getAsJsonArray();
                    double arm = a.get(0).getAsDouble(), tough = a.get(1).getAsDouble();
                    Double kb = a.size() > 2 ? a.get(2).getAsDouble() : null;
                    items.merge(e.getKey(), NONE.withArmor(arm, tough, kb), (old, add) -> old.withArmor(arm, tough, kb));
                } else warn("armor", e.getKey());
            });
        }
        JsonObject durability = object(json, "durability");
        if (durability != null) {
            durability.entrySet().forEach(e -> {
                JsonElement v = e.getValue();
                int value;
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString() && v.getAsString().equalsIgnoreCase("unbreakable")) {
                    value = UNBREAKABLE;
                } else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && v.getAsInt() > 0) {
                    value = v.getAsInt();
                } else {
                    warn("durability", e.getKey());
                    return;
                }
                items.merge(e.getKey(), NONE.withDurability(value), (old, add) -> old.withDurability(value));
            });
        }
        JsonElement noDamage = json.get("no_damage");
        if (noDamage != null && noDamage.isJsonArray()) {
            noDamage.getAsJsonArray().forEach(v -> {
                if (v.isJsonPrimitive()) {
                    items.merge(v.getAsString(), NONE.withNoDamage(), (old, add) -> old.withNoDamage());
                }
            });
        }
        return items;
    }

    private static JsonObject object(JsonObject json, String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static void warn(String section, String id) {
        CombatRebalance.LOG.warn("[{}] Ignoring {} entry for {}: unexpected value", CombatRebalance.MOD_ID, section, id);
    }

    // ------------------------------------------------------------------------------------------------------
    // Defaults

    private static JsonObject defaults() {
        JsonObject json = new JsonObject();
        json.addProperty("_about", "Create: Mint & Machine - Combat Rebalance. Restart the game after editing. "
                + "damage: attack damage as the tooltip shows it (the player's base 1 plus the item's bonus). "
                + "armor: [armor, toughness] or [armor, toughness, knockback resistance] per piece. "
                + "durability: maximum durability, or \"unbreakable\". no_damage: items that lose attack damage and speed. "
                + "Only each item's own default modifiers are replaced; bonuses added by other mods are kept.");

        JsonObject damage = new JsonObject();
        damage.addProperty("create_sa:zinc_axe", 7);
        damage.addProperty("create_sa:rose_quartz_axe", 8);
        String[] tiers = {"inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium"};
        double[][] tools = {
                // sword, scythe, sickle, axe, pickaxe, shovel, hoe
                {6, 7, 7, 9, 4, 4.5, 2},
                {7, 8, 8, 9, 5, 5.5, 3},
                {8, 9, 9, 10, 6, 6.5, 4},
                {9, 10, 10, 11, 7, 7.5, 5},
                {10, 11, 11, 12, 8, 8.5, 6},
                {11, 12, 12, 13, 9, 9.5, 7},
        };
        String[] toolNames = {"sword", "scythe", "sickle", "axe", "pickaxe", "shovel", "hoe"};
        for (int t = 0; t < tiers.length; t++) {
            for (int k = 0; k < toolNames.length; k++) {
                damage.addProperty("mysticalagriculture:" + tiers[t] + "_" + toolNames[k], tools[t][k]);
            }
        }
        json.add("damage", damage);

        JsonObject armor = new JsonObject();
        double[][] maArmor = {
                // helmet, chestplate, leggings, boots armor; toughness per piece
                {2, 6, 5, 2, 0.0},
                {3, 7, 5, 3, 0.5},
                {3, 8, 6, 3, 1.5},
                {3, 8, 6, 3, 2.5},
                {3, 8, 6, 3, 3.0},
                {3, 8, 7, 4, 3.5},
        };
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int t = 0; t < tiers.length; t++) {
            for (int p = 0; p < pieces.length; p++) {
                armor.add("mysticalagriculture:" + tiers[t] + "_" + pieces[p], arr(maArmor[t][p], maArmor[t][4]));
            }
        }
        set(armor, "forbidden_arcanus:tyr_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 8, 7, 4}, 3.0, null);
        set(armor, "forbidden_arcanus:draco_arcanus_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 8, 6, 4}, 2.5, null);
        for (String element : new String[]{"fire", "aqua", "earth", "air"}) {
            set(armor, "ars_elemental:" + element + "_", new String[]{"helmet", "chestplate", "leggings_heavy", "boots_heavy"},
                    new double[]{3, 8, 6, 3}, 3.0, null);
        }
        set(armor, "ars_technica:machinaguard_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 8, 6, 3}, 3.0, null);
        // Create: Ironworks
        set(armor, "create_ironworks:rose_quartz_armor_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 6, 5, 3}, 0, null);
        set(armor, "create_ironworks:sturdy_armor_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 7, 5, 3}, 1, null);
        set(armor, "create_ironworks:steel_armor_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{3, 8, 6, 3}, 1.5, 0.1);
        // Create Stuff & Netherite Additions
        armor.add("create_nj:netherite_exoskeleton_chestplate", arr(10, 4, 0.15));
        // Protection Pixel
        set(armor, "protection_pixel:linkplate_", new String[]{"helmet", "chestplate", "leggings", "boots"}, new double[]{2, 6, 5, 2}, 2, null);
        json.add("armor", armor);

        JsonObject durability = new JsonObject();
        durability.addProperty("create_nj:netherite_exoskeleton_chestplate", "unbreakable");
        durability.addProperty("create_nj:netherite_jetpack_chestplate", "unbreakable");
        json.add("durability", durability);

        JsonArray noDamage = new JsonArray();
        noDamage.add("herbalbrews:jug");
        json.add("no_damage", noDamage);
        return json;
    }

    private static void set(JsonObject armor, String prefix, String[] names, double[] values, double toughness, Double knockback) {
        for (int i = 0; i < names.length; i++) {
            armor.add(prefix + names[i], knockback == null ? arr(values[i], toughness) : arr(values[i], toughness, knockback));
        }
    }

    private static JsonArray arr(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(v);
        return a;
    }
}
