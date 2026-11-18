package com.mintmachine.economy;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;

/**
 * config/mintmachine_economy/settings.json: every setting of the four parts, written with the defaults the first
 * time the game starts. Read again each time a world starts, so a change needs only a world reload. A key missing
 * from the file keeps its default.
 */
public final class EconomySettings {
    static final String FOLDER = MintMachineEconomy.MOD_ID;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static volatile EconomySettings current = new EconomySettings(defaults());

    public static EconomySettings get() {
        return current;
    }

    static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(FOLDER);
    }

    // ------------------------------------------------------------------------------------------- villager_coins
    /** Price in spurs = scale x (emeralds ^ curve). */
    final double curve;
    final double scale;
    /** The curve trades were converted with before; such villagers are re-priced once to the current curve. */
    final double oldCurve;
    final double oldScale;
    /** How far a villager price may be rounded so it fits in a single stack of one coin. */
    final double villagerRounding;
    /** Villager Currency's own rate, so a trade it already changed can be read back as emeralds. */
    final double villagerCurrencySpursPerEmerald;
    final boolean villagerDebug;

    // ------------------------------------------------------------------------------------------ shipping_values
    final double stepMarkup;
    final double stepLabor;
    final double foodMarkup;
    final double foodLabor;
    final double rounding;
    final int maxBatch;
    final double villagerCap;
    final double[] fallbackTiers;
    final int unstackableBonus;
    final int bossDropBonus;
    final Map<String, Double> priceMultipliers;
    final List<String> bosses;
    final List<String> notBosses;
    final double bossMinHealth;
    final List<String> chiselMods;
    final List<String> explain;
    final List<String> primaryTypes;
    final int villagerSamples;
    final List<String> skipProfessions;
    final Map<String, Double> materials;
    /** Items and #tags in spurs, in order: the first match wins. */
    final List<Map.Entry<String, Double>> base;
    final List<String> excludeIds;
    final List<String> excludeParts;
    final List<String> defaultTrades;
    final boolean forceReprice;
    /** The shipping_values section as written, for the price fingerprint. */
    final String shippingFingerprint;

    // ------------------------------------------------------------------------------------------ coin_change
    final boolean tidy;
    final boolean villagerCard;
    final boolean shops;
    final boolean changeDebug;

    // ------------------------------------------------------------------------------------------ fish_bonus
    final double goldenMultiplier;
    final double biggestMultiplier;

    private EconomySettings(JsonObject json) {
        JsonObject v = section(json, "villager_coins");
        curve = num(v, "curve");
        scale = num(v, "scale");
        oldCurve = num(v, "old_curve");
        oldScale = num(v, "old_scale");
        villagerRounding = num(v, "rounding");
        villagerCurrencySpursPerEmerald = num(v, "villager_currency_spurs_per_emerald");
        villagerDebug = bool(v, "debug");

        JsonObject s = section(json, "shipping_values");
        stepMarkup = num(s, "step_markup");
        stepLabor = num(s, "step_labor");
        foodMarkup = num(s, "food_markup");
        foodLabor = num(s, "food_labor");
        rounding = num(s, "rounding");
        maxBatch = (int) num(s, "max_batch");
        villagerCap = num(s, "villager_cap");
        JsonArray tiers = s.getAsJsonArray("fallback_tiers");
        fallbackTiers = new double[tiers.size()];
        for (int i = 0; i < tiers.size(); i++) fallbackTiers[i] = tiers.get(i).getAsDouble();
        unstackableBonus = (int) num(s, "unstackable_bonus");
        bossDropBonus = (int) num(s, "boss_drop_bonus");
        priceMultipliers = numbers(s, "price_multipliers");
        bosses = strings(s, "bosses");
        notBosses = strings(s, "not_bosses");
        bossMinHealth = num(s, "boss_min_health");
        chiselMods = strings(s, "chisel_mods");
        explain = strings(s, "explain");
        primaryTypes = strings(s, "primary_types");
        villagerSamples = (int) num(s, "villager_samples");
        skipProfessions = strings(s, "skip_professions");
        materials = numbers(s, "materials");
        base = List.copyOf(numbers(s, "base").entrySet());
        excludeIds = strings(s, "exclude_ids");
        excludeParts = strings(s, "exclude_parts");
        defaultTrades = strings(s, "default_trades");
        forceReprice = bool(s, "force_reprice");
        JsonObject forPrint = s.deepCopy();
        forPrint.remove("explain");
        forPrint.remove("force_reprice");
        forPrint.remove("_about");
        shippingFingerprint = forPrint.toString();

        JsonObject c = section(json, "coin_change");
        tidy = bool(c, "tidy");
        villagerCard = bool(c, "villager_card");
        shops = bool(c, "shops");
        changeDebug = bool(c, "debug");

        JsonObject f = section(json, "fish_bonus");
        goldenMultiplier = num(f, "golden_multiplier");
        biggestMultiplier = num(f, "biggest_multiplier");
    }

    /** Reads the file (writing the defaults first if there is none). Keeps the previous settings if it can't. */
    static void reload() {
        Path path = folder().resolve("settings.json");
        JsonObject json = defaults();
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonObject file = JsonParser.parseReader(reader).getAsJsonObject();
                // Each key the file has replaces the default; missing keys keep their defaults
                for (Map.Entry<String, JsonElement> sec : file.entrySet()) {
                    if (!sec.getValue().isJsonObject() || !json.has(sec.getKey())) continue;
                    JsonObject target = json.getAsJsonObject(sec.getKey());
                    for (Map.Entry<String, JsonElement> e : sec.getValue().getAsJsonObject().entrySet()) {
                        target.add(e.getKey(), e.getValue());
                    }
                }
                current = new EconomySettings(json);
            } catch (Exception e) {
                MintMachineEconomy.LOG.error("[{}] Could not read config/{}/settings.json, keeping the previous settings: {}",
                        MintMachineEconomy.MOD_ID, FOLDER, e.toString());
            }
        } else {
            current = new EconomySettings(json);
            try {
                Files.createDirectories(path.getParent());
                try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaults(), writer);
                }
            } catch (IOException e) {
                MintMachineEconomy.LOG.warn("[{}] Could not write the default settings: {}", MintMachineEconomy.MOD_ID, e.toString());
            }
        }
    }

    static void onServerAboutToStart(ServerAboutToStartEvent event) {
        reload();
    }

    // ------------------------------------------------------------------------------------------ JSON helpers

    private static JsonObject section(JsonObject json, String key) {
        return json.getAsJsonObject(key);
    }

    private static double num(JsonObject o, String key) {
        return o.get(key).getAsDouble();
    }

    private static boolean bool(JsonObject o, String key) {
        return o.get(key).getAsBoolean();
    }

    private static List<String> strings(JsonObject o, String key) {
        List<String> out = new ArrayList<>();
        o.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        return List.copyOf(out);
    }

    private static Map<String, Double> numbers(JsonObject o, String key) {
        Map<String, Double> out = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : o.getAsJsonObject(key).entrySet()) {
            if (e.getKey().startsWith("_")) continue;
            out.put(e.getKey(), e.getValue().getAsDouble());
        }
        return out;
    }

    private static JsonArray array(String... values) {
        JsonArray a = new JsonArray();
        for (String v : values) a.add(v);
        return a;
    }

    private static JsonArray array(double... values) {
        JsonArray a = new JsonArray();
        for (double v : values) a.add(v);
        return a;
    }

    private static void put(JsonObject o, Object... pairs) {
        for (int i = 0; i < pairs.length; i += 2) {
            Object value = pairs[i + 1];
            o.addProperty((String) pairs[i], ((Number) value));
        }
    }

    // ------------------------------------------------------------------------------------------ defaults

    static JsonObject defaults() {
        JsonObject json = new JsonObject();

        JsonObject v = new JsonObject();
        v.addProperty("_about", "Every villager and wandering trader trades in Numismatics coins, priced across the whole coin "
                + "ladder (spur 1, bevel 8, sprocket 16, cog 64, crown 512, sun 4096). Price in spurs = scale x (emeralds ^ curve): "
                + "curve 1.5 makes 1 emerald = 1 spur, 10 = 32, 64 = 512 (1 crown); curve 2 makes 64 emeralds = 1 sun; curve 1 is flat. "
                + "Villagers converted with old_curve/old_scale are re-priced to the current curve the next time someone opens their trades. "
                + "rounding: how far a price may be rounded so it fits in a single stack of one coin (0.05 = 5%). "
                + "villager_currency_spurs_per_emerald: Villager Currency's own rate, so trades it already changed can be read back.");
        v.addProperty("curve", 1.5);
        v.addProperty("scale", 1);
        v.addProperty("old_curve", 2);
        v.addProperty("old_scale", 1);
        v.addProperty("rounding", 0.05);
        v.addProperty("villager_currency_spurs_per_emerald", 8);
        v.addProperty("debug", false);
        json.add("villager_coins", v);

        JsonObject s = new JsonObject();
        s.addProperty("_about", "Shipping Bin sell prices for every item, in spurs. Raw materials get a base value (base, materials); "
                + "everything craftable is priced from its cheapest recipe plus step_markup and step_labor per step (food_markup and "
                + "food_labor for recipes that combine two or more ingredients into food); storage blocks and their contents are always "
                + "worth the same; anything else gets fallback_tiers by rarity (common, uncommon, rare, epic, then two higher tiers), "
                + "+unstackable_bonus tiers if it doesn't stack and can't be crafted, +boss_drop_bonus tiers if a boss drops it. "
                + "Rechiseled/Chipped variants (chisel_mods) sell for the same as their block. Nothing pays more than villager_cap of what "
                + "a villager charges. price_multipliers are hand-tuned boosts (villagers selling the item charge the same multiple more). "
                + "Prices are worked out when a world loads and saved to config/mintmachine_economy/shipping_prices.json; they are worked "
                + "out again when these settings, the mod list, the number of items or the recipe ids change, or with force_reprice.");
        put(s, "step_markup", 0.10, "step_labor", 0.02, "food_markup", 1.0, "food_labor", 0.25, "rounding", 0.05,
                "max_batch", 640, "villager_cap", 0.5);
        s.add("fallback_tiers", array(0.25, 4, 32, 256, 2048, 16384));
        put(s, "unstackable_bonus", 1, "boss_drop_bonus", 2);
        JsonObject mult = new JsonObject();
        put(mult, "minecraft:golden_carrot", 8, "minecraft:golden_apple", 8, "minecraft:cookie", 2);
        s.add("price_multipliers", mult);
        s.add("bosses", array("minecraft:wither", "minecraft:ender_dragon", "minecraft:elder_guardian", "minecraft:warden"));
        s.add("not_bosses", new JsonArray());
        put(s, "boss_min_health", 150);
        s.add("chisel_mods", array("rechiseled", "chipped"));
        s.add("explain", array("minecraft:iron_ingot", "minecraft:iron_block", "minecraft:iron_nugget",
                "minecraft:gold_block", "rechiseled:iron_block_bordered", "chipped:ancient_iron_block",
                "botania:terrasteel_ingot", "botania:manasteel_block", "minecraft:bread", "farmersdelight:beef_stew"));
        s.add("primary_types", array("minecraft:crafting", "minecraft:smelting", "minecraft:blasting", "minecraft:smoking",
                "minecraft:campfire_cooking", "minecraft:stonecutting", "minecraft:smithing", "create:mechanical_crafting"));
        put(s, "villager_samples", 4);
        s.add("skip_professions", array("minecraft:none", "minecraft:nitwit", "minecraft:cartographer"));
        JsonObject materials = new JsonObject();
        put(materials, "coal", 0.25, "copper", 0.5, "redstone", 0.3, "lapis", 0.5, "quartz", 0.6, "amethyst", 0.5,
                "zinc", 1, "tin", 1, "aluminum", 1.5, "lead", 2, "iron", 2, "nickel", 2.5, "silver", 3, "lithium", 3,
                "osmium", 3, "gold", 4, "uranium", 6, "thorium", 6, "platinum", 12, "emerald", 16, "diamond", 32);
        s.add("materials", materials);
        s.add("base", defaultBase());
        s.add("exclude_ids", array("minecraft:air", "minecraft:bedrock", "minecraft:barrier", "minecraft:light",
                "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:end_portal_frame",
                "minecraft:reinforced_deepslate", "minecraft:petrified_oak_slab", "minecraft:structure_void",
                "numismatics:spur", "numismatics:bevel", "numismatics:sprocket", "numismatics:cog",
                "numismatics:crown", "numismatics:sun"));
        s.add("exclude_parts", array("spawn_egg", "command_block", "structure_block", "jigsaw", "spawner", "creative"));
        s.add("default_trades", array("amethyst", "apple", "baked_potato", "beef", "beetroot", "beetroot_soup", "bread",
                "cake", "carrot", "chicken", "chorus_fruit", "cocoa_beans", "cod", "cookie", "copper_block", "copper_ingot",
                "diamond", "diamond_block", "dried_kelp", "glow_berries", "gold_block", "gold_ingot", "golden_apple",
                "golden_carrot", "hay_bale", "honey_bottle", "iron_block", "iron_ingot", "logs", "melon", "melon_slice",
                "mushroom_stew", "mutton", "netherite_block", "netherite_ingot", "notch_apple", "planks", "porkchop",
                "potato", "pufferfish", "pumpkin", "pumpkin_pie", "quartz", "rabbit", "salmon", "suspicious_stew",
                "sweet_berries", "tropical_fish", "wheat"));
        s.addProperty("force_reprice", false);
        json.add("shipping_values", s);

        JsonObject c = new JsonObject();
        c.addProperty("_about", "Change for Numismatics coins. tidy: merge loose change back up the coin ladder afterwards. "
                + "villager_card: a bound Bank Card in the offhand pays at villagers. shops: make exact change at Numismatics "
                + "Vendors, Brass Depositors and Create shopkeepers.");
        c.addProperty("tidy", true);
        c.addProperty("villager_card", true);
        c.addProperty("shops", true);
        c.addProperty("debug", false);
        json.add("coin_change", c);

        JsonObject f = new JsonObject();
        f.addProperty("_about", "Golden and big Starcatcher fish sell for more in the Shipping Bin: golden fish x golden_multiplier; "
                + "a fish in the top 50% by size from x1 at the middle up to x biggest_multiplier for the very biggest.");
        f.addProperty("golden_multiplier", 10);
        f.addProperty("biggest_multiplier", 2);
        json.add("fish_bonus", f);
        return json;
    }

    private static JsonObject defaultBase() {
        Object[] b = {
                // Rare finds and boss drops
                "minecraft:dragon_egg", 16384, "minecraft:elytra", 8192, "minecraft:nether_star", 4096,
                "minecraft:dragon_head", 2048, "minecraft:enchanted_golden_apple", 2048, "minecraft:heavy_core", 1024,
                "minecraft:heart_of_the_sea", 512, "minecraft:totem_of_undying", 512, "minecraft:trident", 512,
                "minecraft:ancient_debris", 160, "minecraft:wither_skeleton_skull", 128,
                "minecraft:netherite_upgrade_smithing_template", 64, "#minecraft:trim_templates", 16,
                "#c:music_discs", 64, "minecraft:shulker_shell", 64, "minecraft:diamond_horse_armor", 64,
                "minecraft:echo_shard", 32, "minecraft:bell", 32, "minecraft:enchanted_book", 16,
                "minecraft:nautilus_shell", 16, "minecraft:goat_horn", 16, "minecraft:saddle", 16,
                "minecraft:name_tag", 16, "minecraft:golden_horse_armor", 16, "minecraft:wet_sponge", 16,
                "minecraft:sponge", 16, "minecraft:dragon_breath", 16, "minecraft:ghast_tear", 12,
                "minecraft:iron_horse_armor", 8, "minecraft:turtle_scute", 8, "minecraft:breeze_rod", 6,
                // Botania and Ars Nouveau: mana and Source aren't items, so recipes can't count them
                "botania:gaia_ingot", 512, "botania:terrasteel_ingot", 128, "botania:dragonstone", 64,
                "botania:mana_diamond", 48, "botania:life_essence", 32, "botania:elementium_ingot", 16,
                "botania:pixie_dust", 16, "botania:mana_pearl", 10, "botania:manasteel_ingot", 6,
                "botania:mana_string", 1, "botania:mana_powder", 0.5,
                "botania:rune_lust", 24, "botania:rune_gluttony", 24, "botania:rune_greed", 24, "botania:rune_sloth", 24,
                "botania:rune_wrath", 24, "botania:rune_envy", 24, "botania:rune_pride", 24, "botania:rune_mana", 16,
                "botania:rune_spring", 8, "botania:rune_summer", 8, "botania:rune_autumn", 8, "botania:rune_winter", 8,
                "botania:rune_water", 4, "botania:rune_fire", 4, "botania:rune_earth", 4, "botania:rune_air", 4,
                "ars_nouveau:abjuration_essence", 8, "ars_nouveau:conjuration_essence", 8, "ars_nouveau:manipulation_essence", 8,
                "ars_nouveau:air_essence", 8, "ars_nouveau:earth_essence", 8, "ars_nouveau:fire_essence", 8,
                "ars_nouveau:water_essence", 8, "ars_nouveau:source_gem", 2,
                // Mob drops
                "#c:rods/blaze", 4, "#c:ender_pearls", 3, "minecraft:phantom_membrane", 3, "minecraft:rabbit_foot", 3,
                "minecraft:armadillo_scute", 2, "minecraft:glow_ink_sac", 0.5, "#c:slime_balls", 0.3,
                "#c:gunpowders", 0.3, "minecraft:honeycomb", 0.3, "#c:leathers", 0.25, "minecraft:spider_eye", 0.15,
                "minecraft:rabbit_hide", 0.15, "minecraft:ink_sac", 0.1, "#minecraft:wool", 0.1, "#c:bones", 0.08,
                "#c:strings", 0.08, "#c:feathers", 0.05, "#c:eggs", 0.05, "minecraft:rotten_flesh", 0.02,
                // Nether, End and ocean
                "minecraft:crying_obsidian", 1.5, "minecraft:prismarine_crystals", 0.75, "minecraft:obsidian", 0.5,
                "minecraft:prismarine_shard", 0.5, "#c:dusts/glowstone", 0.3, "minecraft:chorus_fruit", 0.2,
                "minecraft:nether_wart", 0.1, "minecraft:magma_block", 0.1, "minecraft:sculk", 0.1,
                "minecraft:end_stone", 0.05, "minecraft:soul_sand", 0.03, "minecraft:soul_soil", 0.03,
                // Starcatcher fish, by the rarity Starcatcher gives them
                "starcatcher:eel_dynamo", 16,
                "starcatcher:cogtopus", 6, "starcatcher:drive_pike", 6, "starcatcher:mechanical_brass_snail", 6,
                "starcatcher:meka_agave_bream", 6, "starcatcher:pipehead", 6,
                "starcatcher:phillipsfish", 1, "starcatcher:valve", 1,
                "#starcatcher:legendary_starcaught_fishes", 64, "#starcatcher:epic_starcaught_fishes", 16,
                "#starcatcher:rare_starcaught_fishes", 6, "#starcatcher:common_starcaught_fishes", 1,
                "#starcatcher:starcaught_fishable", 2,
                // Food and farming
                "minecraft:honey_bottle", 0.4, "#c:foods/raw_meat", 0.15, "#c:foods/raw_fish", 0.15,
                "minecraft:pumpkin", 0.15, "#c:foods/fruit", 0.1, "minecraft:cocoa_beans", 0.08,
                "#c:foods/vegetable", 0.06, "#c:crops", 0.06, "#c:foods/berry", 0.05, "#c:mushrooms", 0.05,
                "#minecraft:flowers", 0.05, "#minecraft:saplings", 0.05, "minecraft:cactus", 0.05,
                "minecraft:sea_pickle", 0.05, "minecraft:moss_block", 0.05, "minecraft:sugar_cane", 0.04,
                "minecraft:melon_slice", 0.03, "minecraft:bamboo", 0.03, "#c:seeds", 0.02, "minecraft:kelp", 0.02,
                "minecraft:vine", 0.02, "#minecraft:leaves", 0.01,
                // Wood, stone and earth
                "#minecraft:logs", 0.15, "minecraft:clay_ball", 0.04, "minecraft:flint", 0.04,
                "minecraft:pointed_dripstone", 0.03, "minecraft:ice", 0.03, "minecraft:calcite", 0.02,
                "#c:sands", 0.015, "#c:gravels", 0.015, "#c:stones", 0.015, "minecraft:mud", 0.015,
                "minecraft:basalt", 0.015, "minecraft:blackstone", 0.015, "minecraft:tuff", 0.015,
                "#c:cobblestones", 0.01, "#minecraft:dirt", 0.01, "#c:netherracks", 0.01, "minecraft:snowball", 0.01,
        };
        JsonObject o = new JsonObject();
        o.addProperty("_about", "Specific items and #tags, in spurs. The first match wins.");
        put(o, b);
        return o;
    }
}
