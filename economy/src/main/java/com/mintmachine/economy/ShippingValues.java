package com.mintmachine.economy;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.datafixers.util.Pair;

import net.minecraft.core.HolderSet;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import tfar.shippingbin.ShippingBin;

/**
 * Gives every item a Numismatics sell price in the Shipping Bin (port of shipping_values.js).
 * <ol>
 * <li>Raw materials get a base value by rarity (the base and materials settings).</li>
 * <li>Everything craftable is priced from its cheapest recipe: the ingredients' value plus a markup for every crafting
 * or processing step. Storage blocks and their contents are always worth exactly the same, and pay out the same.</li>
 * <li>Anything still unpriced gets a fallback from its in-game rarity, moved up a tier if it doesn't stack and can't
 * be crafted, and two tiers if a boss drops it.</li>
 * <li>Rechiseled and Chipped variants sell for the same as the block they're chiseled from.</li>
 * <li>Nothing pays more than half of what a villager charges for it.</li>
 * </ol>
 * Worked out when a world has loaded, saved to config/mintmachine_economy/shipping_prices.json, and served to the
 * Shipping Bin by {@link ShippingPack}. Values are in spurs.
 */
final class ShippingValues {
    static final String LOG = "[shipping_values] ";
    /** Bump when the calculation changes, so saved prices are worked out again. */
    private static final String VERSION = "java-1";
    private static final List<String> RARITIES = List.of("COMMON", "UNCOMMON", "RARE", "EPIC");

    static void onServerStarted(ServerStartedEvent event) {
        try {
            new ShippingValues(event.getServer()).run();
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(LOG + "Could not work out Shipping Bin prices", e);
        }
    }

    private static void log(String msg) {
        MintMachineEconomy.LOG.info(LOG + msg);
    }

    // ------------------------------------------------------------------------------------------ state

    private final MinecraftServer server;
    private final EconomySettings s;
    private final RegistryAccess access;
    private final Map<String, Integer> problems = new LinkedHashMap<>();

    private final List<String> allIds = new ArrayList<>();
    private final Set<String> exists = new HashSet<>();
    private final Set<String> excluded = new LinkedHashSet<>();
    private final Map<String, Double> value = new HashMap<>();
    private final Set<String> fixed = new HashSet<>();
    private final Map<String, String> source = new HashMap<>();
    private final Map<String, Integer> maxStackCache = new HashMap<>();

    private final List<Rec> recipes = new ArrayList<>();
    private final Map<String, List<Integer>> usedBy = new HashMap<>();
    private final Set<String> craftable = new HashSet<>();

    private record Link(String p, double f) {}

    private record Root(String root, double f) {}

    private final Map<String, Link> parent = new LinkedHashMap<>();
    private final Map<String, List<String>> groupMembers = new LinkedHashMap<>();
    private final Map<String, Double> factor = new HashMap<>();

    private final Set<String> bossDrops = new LinkedHashSet<>();
    private final Map<String, Double> villagerUnit = new LinkedHashMap<>();

    /** A recipe as the calculation sees it: each ingredient is the list of items that can fill it. */
    private static final class Rec {
        final String id;
        final String type;
        final boolean primary;
        final String out;
        final int count;
        final List<List<String>> ings;
        boolean cooking;

        Rec(String id, String type, boolean primary, String out, int count, List<List<String>> ings) {
            this.id = id;
            this.type = type;
            this.primary = primary;
            this.out = out;
            this.count = count;
            this.ings = ings;
        }
    }

    /** One sale: count items for coinCount of coin. */
    record Sale(int count, String coin, int coinCount) {
        double payout() {
            return (double) Coins.valueOf(coin) * coinCount / count;
        }
    }

    private ShippingValues(MinecraftServer server) {
        this.server = server;
        this.s = EconomySettings.get();
        this.access = server.registryAccess();
    }

    @FunctionalInterface
    private interface Step<T> {
        T run() throws Exception;
    }

    /** Runs one step; if it fails, notes the problem for the log instead of stopping everything. */
    private <T> T attempt(String step, Step<T> fn) {
        try {
            return fn.run();
        } catch (Exception | LinkageError e) {
            String key = step + ": " + e;
            if (problems.containsKey(key)) problems.merge(key, 1, Integer::sum);
            else if (problems.size() < 12) problems.put(key, 1);
            return null;
        }
    }

    // ------------------------------------------------------------------------------------------ helpers

    private static String idOf(Item item) {
        return Coins.idOf(item);
    }

    private static Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
    }

    private static String ns(String id) {
        int i = id.indexOf(':');
        return i < 0 ? id : id.substring(0, i);
    }

    private static String path(String id) {
        int i = id.indexOf(':');
        if (i < 0) return "";
        int j = id.indexOf(':', i + 1);
        return j < 0 ? id.substring(i + 1) : id.substring(i + 1, j);
    }

    /** Like JavaScript's text.replace(find, with): the first occurrence only. */
    private static String replaceFirst(String text, String find, String with) {
        int i = text.indexOf(find);
        return i < 0 ? text : text.substring(0, i) + with + text.substring(i + find.length());
    }

    /** A number the way JavaScript prints it (8, not 8.0). */
    static String jsNum(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }

    private List<String> tagItems(String tag) {
        List<String> out = new ArrayList<>();
        ResourceLocation rl = ResourceLocation.tryParse(tag);
        if (rl == null) return out;
        Optional<HolderSet.Named<Item>> named = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, rl));
        if (named.isEmpty()) return out;
        HolderSet.Named<Item> set = named.get();
        for (int i = 0; i < set.size(); i++) out.add(idOf(set.get(i).value()));
        return out;
    }

    private int maxStackOf(String id) {
        return maxStackCache.computeIfAbsent(id, k -> {
            Integer max = attempt("stack size", () -> new ItemStack(item(k)).getMaxStackSize());
            return max == null || max == 0 ? 64 : max;
        });
    }

    private List<String> itemsOf(Ingredient ing) {
        ItemStack[] stacks = attempt("ingredient items", ing::getItems);
        if (stacks == null) return List.of();
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (ItemStack st : stacks) {
            String id = attempt("ingredient item id", () -> idOf(st.getItem()));
            if (id != null) items.add(id);
        }
        return new ArrayList<>(items);
    }

    private Root find(String id) {
        double f = 1;
        String cur = id;
        Link l;
        while ((l = parent.get(cur)) != null) {
            f *= l.f;
            cur = l.p;
        }
        return new Root(cur, f);
    }

    private List<String> membersOf(String id) {
        List<String> m = groupMembers.get(find(id).root);
        return m != null ? m : List.of(id);
    }

    private String describeRatio(String m, String anchor) {
        double ratio = factor.get(m) / factor.get(anchor);
        return ratio >= 1 ? "equal to " + Math.round(ratio) + " " + anchor : "1/" + Math.round(1 / ratio) + " of " + anchor;
    }

    private Set<String> snapshot() {
        return new HashSet<>(value.keySet());
    }

    // ------------------------------------------------------------------------------------------ the run

    private void run() {
        long started = System.currentTimeMillis();

        // ---- Skip the whole calculation if nothing it depends on has changed since last time ----
        String modList = Objects.requireNonNullElse(attempt("mod list", () -> ModList.get().getMods().stream()
                .map(m -> m.getModId() + "@" + m.getVersion())
                .sorted()
                .collect(Collectors.joining(","))), "");
        String itemSig = Objects.requireNonNullElse(attempt("item list", () -> {
            int sum = 0;
            for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) sum += key.hashCode();
            return BuiltInRegistries.ITEM.keySet().size() + "/" + sum;
        }), String.valueOf(BuiltInRegistries.ITEM.size()));
        Collection<RecipeHolder<?>> holders = server.getRecipeManager().getRecipes();
        String recipeSig = Objects.requireNonNullElse(attempt("recipe list", () -> {
            int sum = 0;
            for (RecipeHolder<?> h : holders) sum += h.id().hashCode();
            return holders.size() + "/" + sum;
        }), "");
        log("Checking for changes: " + itemSig.split("/")[0] + " items, " + (recipeSig.isEmpty() ? "?" : recipeSig.split("/")[0])
                + " recipes, " + (modList.isEmpty() ? "?" : String.valueOf(modList.split(",").length)) + " mods");
        String settingsPrint = "[" + VERSION + "," + s.shippingFingerprint + ",curve=" + s.curve + ",scale=" + s.scale
                + ",vc=" + s.villagerCurrencySpursPerEmerald + "]";
        String fingerprint = settingsPrint + "|items:" + itemSig + "|recipes:" + recipeSig + "|mods:" + modList;

        JsonObject cached = PriceCache.read();
        boolean same = cached != null && !s.forceReprice && cached.has("fingerprint") && cached.has("trades")
                && fingerprint.equals(cached.get("fingerprint").getAsString());
        if (same) {
            // Saved prices for items that no longer exist (a mod was removed) mean the file is out of date
            int gone = 0;
            for (String id : cached.getAsJsonObject("trades").keySet()) {
                ResourceLocation rl = ResourceLocation.tryParse(id);
                if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) gone++;
            }
            if (gone > 0) {
                log(gone + " saved prices are for items that no longer exist (a mod was removed); working prices out again.");
                same = false;
            }
        }
        if (same) {
            JsonObject savedTrades = cached.getAsJsonObject("trades");
            Integer liveNow = attempt("live trade count", () -> ShippingBin.getTradeManager().getTrades().size());
            if (liveNow == null || liveNow == savedTrades.size()) {
                log(VERSION + " | Nothing changed since prices were last worked out; using the " + savedTrades.size()
                        + " saved prices (" + (System.currentTimeMillis() - started) + " ms).");
                return;
            }
            log("The Shipping Bin has " + liveNow + " trades loaded but " + savedTrades.size() + " are saved; applying the saved prices.");
            applyOrReload(savedTrades, true);
            return;
        }

        // ----- All items -----
        for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) allIds.add(key.toString());
        exists.addAll(allIds);
        for (String id : allIds) {
            if (s.excludeIds.contains(id) || s.excludeParts.stream().anyMatch(id::contains)) excluded.add(id);
        }

        // ----- Base values (fixed: recipes never change them) -----
        for (Map.Entry<String, Double> b : s.base) {
            if (b.getKey().startsWith("#")) setTag(b.getKey().substring(1), b.getValue());
            else setBase(b.getKey(), b.getValue());
        }
        s.materials.forEach((m, v) -> {
            setTag("c:raw_materials/" + m, v);
            setTag("c:gems/" + m, v);
            setTag("c:dusts/" + m, v);
            setTag("c:ingots/" + m, v * 1.15);
            setTag("c:ores/" + m, v * 1.5);
        });
        if (s.materials.containsKey("coal")) setBase("minecraft:coal", s.materials.get("coal"));
        setTag("c:raw_materials", 2);
        setTag("c:gems", 6);
        setTag("c:ores", 3);
        int baseCount = fixed.size();

        // ----- Recipes -----
        readRecipes(holders);
        log("Read " + recipes.size() + " of " + holders.size() + " recipes");
        for (int n = 0; n < recipes.size(); n++) {
            for (List<String> items : recipes.get(n).ings) {
                for (String id : items) usedBy.computeIfAbsent(id, k -> new ArrayList<>()).add(n);
            }
        }
        for (Rec r : recipes) if (r.type.equals("minecraft:crafting")) craftable.add(r.out);

        // Cooking recipes: two or more ingredients making something edible (or tagged as food)
        Set<String> foodTagged = new HashSet<>(tagItems("c:foods"));
        Map<String, Boolean> edible = new HashMap<>();
        int cookingCount = 0;
        for (Rec r : recipes) {
            boolean food = edible.computeIfAbsent(r.out, id -> foodTagged.contains(id)
                    || Boolean.TRUE.equals(attempt("food check", () -> item(id).components().has(DataComponents.FOOD))));
            r.cooking = r.ings.size() >= 2 && food;
            if (r.cooking) cookingCount++;
        }

        int pairCount = buildStorageGroups();

        // ----- Price items from their cheapest recipe -----
        Predicate<Rec> allRecipes = r -> true;
        Predicate<Rec> primaryRecipes = r -> r.primary;
        // Pass 1: normal crafting, smelting, stonecutting and smithing, from base materials
        propagate(primaryRecipes, id -> true);
        int primaryCount = value.size() - baseCount;
        // Pass 2: other machine recipes, but only for items normal crafting can't make
        Set<String> pricedA = snapshot();
        propagate(allRecipes, id -> !pricedA.contains(id));
        int machineCount = value.size() - baseCount - primaryCount;
        Set<String> priced = snapshot();

        // ----- Bosses and what they drop -----
        List<String> bossNames = findBossDrops();

        // ----- Rarity fallback -----
        int[] fallback = new int[2]; // count, moved up a tier
        Set<String> isOutput = new HashSet<>();
        for (Rec r : recipes) isOutput.add(r.out);
        for (String id : allIds) {
            if (!excluded.contains(id) && !value.containsKey(id) && !isOutput.contains(id)) giveFallback(id, fallback);
        }
        // Pass 3: things nothing can make get a fallback, then price whatever depends on them
        propagate(primaryRecipes, id -> !priced.contains(id));
        Set<String> pricedB = snapshot();
        propagate(allRecipes, id -> !pricedB.contains(id));
        for (String id : allIds) {
            if (!excluded.contains(id) && !value.containsKey(id)) giveFallback(id, fallback);
        }

        // ----- Hand-tuned boosts -----
        s.priceMultipliers.forEach((id, mult) -> {
            if (!value.containsKey(id)) return;
            value.put(id, value.get(id) * mult);
            String src = source.get(id);
            source.put(id, (src == null || src.isEmpty() ? "price" : src) + ", x" + jsNum(mult) + " boost");
        });

        // ----- Villager prices: nothing may sell for more than villager_cap of what a villager charges -----
        int capped = readVillagerPrices();

        // ----- Turn values into trades -----
        Map<String, Sale> trades = new LinkedHashMap<>();
        for (String id : allIds) {
            if (!excluded.contains(id) && value.containsKey(id)) trades.put(id, buildTrade(value.get(id), maxStackOf(id)));
        }
        storageGroupRates(trades);

        // Everything must still respect villager prices before chisel variants copy their block's price
        villagerUnit.forEach((id, unit) -> {
            Sale t = trades.get(id);
            if (t == null) return;
            double cap = unit * s.villagerCap;
            if (t.payout() > cap * 1.0001) {
                trades.put(id, buildTrade(cap, maxStackOf(id)));
                value.put(id, cap);
                source.put(id, "capped below villager price");
            }
        });

        int chiselCount = chiselVariants(trades);

        // ----- Report -----
        problems.forEach((k, n) -> MintMachineEconomy.LOG.warn(LOG + "Problem (" + n + "x) " + k));
        log("Bosses found (" + bossNames.size() + "): " + String.join(", ", bossNames));
        log(bossDrops.size() + " items are boss drops; " + fallback[1] + " fallback prices were moved up a tier or more");
        log(pairCount + " storage block pairs, " + chiselCount + " Rechiseled/Chipped variants matched to their block, "
                + cookingCount + " cooking recipes using the cooking markup");
        explain(trades);

        // ----- Save, and apply if anything changed -----
        JsonObject saved = new JsonObject();
        List<String> signature = new ArrayList<>();
        for (String id : allIds) {
            Sale t = trades.get(id);
            if (t == null) continue;
            signature.add(id + t.count + "," + t.coin + "," + t.coinCount);
            JsonArray arr = new JsonArray();
            arr.add(t.count);
            arr.add(t.coin);
            arr.add(t.coinCount);
            arr.add(Math.round(value.get(id) * 1000) / 1000.0);
            arr.add(Objects.requireNonNullElse(source.get(id), ""));
            saved.add(id, arr);
        }
        int hash = String.join(";", signature).hashCode();
        int itemHash = String.join(";", saved.keySet()).hashCode();

        Integer oldHash = cached != null && cached.has("hash") ? cached.get("hash").getAsInt() : null;
        Integer oldItemHash = cached != null && cached.has("items") ? cached.get("items").getAsInt() : null;

        Map<String, Integer> finalSources = new TreeMap<>();
        for (String id : saved.keySet()) {
            String src = Objects.requireNonNullElse(source.get(id), "");
            String kind = src.startsWith("rarity fallback") ? "rarity fallback"
                    : src.startsWith("chiseled from") ? "chisel variant"
                    : (src.contains("(packs/unpacks)") || src.startsWith("equal to ") || src.startsWith("1/")) ? "storage group"
                    : src.startsWith("capped") ? "villager cap"
                    : src.equals("base material") ? "base material"
                    : src.contains("recipe") ? "recipe" : "other";
            finalSources.merge(kind, 1, Integer::sum);
        }
        log("Final price sources: " + finalSources.entrySet().stream().map(e -> e.getValue() + " " + e.getKey())
                .collect(Collectors.joining(", ")));
        log(VERSION + " | Priced " + saved.size() + " items in " + (System.currentTimeMillis() - started) + " ms: "
                + baseCount + " base materials, " + primaryCount + " from crafting, " + machineCount + " from machines, "
                + fallback[0] + " from rarity, " + capped + " capped by villager prices, " + excluded.size() + " excluded");

        JsonObject file = new JsonObject();
        file.addProperty("hash", hash);
        file.addProperty("items", itemHash);
        file.addProperty("fingerprint", fingerprint);
        file.add("trades", saved);
        PriceCache.write(file);

        // Also compare with what the Shipping Bin actually loaded, so new items (from a newly added mod) get
        // sellable even if the saved file already looked up to date
        Integer liveCount = attempt("live trade count", () -> ShippingBin.getTradeManager().getTrades().size());
        boolean liveMatches = liveCount == null || liveCount == saved.size();
        if (!liveMatches) {
            log("The Shipping Bin has " + liveCount + " trades loaded but " + saved.size() + " items are priced; updating it.");
            oldItemHash = null;
        }
        if (Objects.equals(oldHash, hash) && liveMatches) {
            log("Prices unchanged.");
            return;
        }
        applyOrReload(saved, !Objects.equals(oldItemHash, itemHash));
    }

    /**
     * Puts the prices into the Shipping Bin straight away (it keeps its trades in memory). If the list of items the
     * bin accepts can't be updated in place and needs to be, reloads the data packs instead, which rebuilds it.
     */
    private void applyOrReload(JsonObject trades, boolean itemsChanged) {
        boolean inputsUpdated = false;
        try {
            ShippingBinBridge.Result result = ShippingBinBridge.apply(trades);
            inputsUpdated = result.inputsUpdated();
            log(result.applied() + " prices applied to the Shipping Bin.");
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(LOG + "Could not apply prices directly ({}); reloading instead.", e.toString());
            itemsChanged = true;
        }
        if (itemsChanged && !inputsUpdated) {
            log("The set of sellable items changed, reloading so they can be put in the bin...");
            server.reloadResources(server.getPackRepository().getSelectedIds()).exceptionally(t -> {
                MintMachineEconomy.LOG.error(LOG + "Reload failed", t);
                return null;
            });
        }
    }

    // ------------------------------------------------------------------------------------------ base values

    private void setBase(String id, double v) {
        if (!exists.contains(id) || excluded.contains(id) || fixed.contains(id)) return;
        value.put(id, v);
        fixed.add(id);
        source.put(id, "base material");
    }

    private void setTag(String tag, double v) {
        for (String id : tagItems(tag)) setBase(id, v);
    }

    // ------------------------------------------------------------------------------------------ recipes

    private void readRecipes(Collection<RecipeHolder<?>> holders) {
        List<RecipeHolder<?>> smithing = new ArrayList<>();
        for (RecipeHolder<?> h : holders) {
            Recipe<?> r = attempt("recipe", h::value);
            if (r == null) continue;
            if (r instanceof SmithingTransformRecipe) smithing.add(h);
            if (Boolean.TRUE.equals(attempt("isSpecial", r::isSpecial))) continue;

            ItemStack result = attempt("result", () -> r.getResultItem(access));
            if (result == null) result = attempt("result (no registries)", () -> r.getResultItem(null));
            if (result == null || result.isEmpty()) continue;

            NonNullList<Ingredient> ings = attempt("ingredients", r::getIngredients);
            if (ings == null) continue;
            List<List<String>> list = new ArrayList<>();
            for (Ingredient ing : ings) {
                if (ing == null || Boolean.TRUE.equals(attempt("ingredient empty", ing::isEmpty))) continue;
                List<String> items = itemsOf(ing);
                if (!items.isEmpty()) list.add(items);
            }
            if (list.isEmpty()) continue;

            String out = idOf(result.getItem());
            int count = result.getCount();
            if (!exists.contains(out)) continue;
            ResourceLocation typeKey = attempt("recipe type", () -> BuiltInRegistries.RECIPE_TYPE.getKey(r.getType()));
            String type = typeKey == null ? "unknown" : typeKey.toString();
            String rid = h.id().toString();
            recipes.add(new Rec(rid, type, s.primaryTypes.contains(type), out, count == 0 ? 1 : count, list));
        }
        // Smithing recipes don't list their ingredients through getIngredients, so read their fields
        for (RecipeHolder<?> h : smithing) {
            try {
                SmithingTransformRecipe st = (SmithingTransformRecipe) h.value();
                ItemStack result = st.getResultItem(access);
                if (result.isEmpty()) continue;
                String out = idOf(result.getItem());
                List<List<String>> list = new ArrayList<>();
                for (Ingredient ing : new Ingredient[]{st.template, st.base, st.addition}) {
                    if (ing == null) continue;
                    List<String> items = itemsOf(ing);
                    if (!items.isEmpty()) list.add(items);
                }
                if (!list.isEmpty() && exists.contains(out)) {
                    recipes.add(new Rec("smithing for " + out, "minecraft:smithing", true, out,
                            Math.max(1, result.getCount()), list));
                }
            } catch (Exception ignored) {
                // skip this one
            }
        }
    }

    // ------------------------------------------------------------------------------------------ storage groups

    /**
     * Things that pack and unpack into each other, like iron nugget, ingot and block. Members of a group always have
     * the same value per unit: value(member) = factor(member) x value(group). Returns how many pairs were found.
     */
    private int buildStorageGroups() {
        Map<String, Integer> unpack = new HashMap<>();
        for (Rec r : recipes) {
            if (r.type.equals("minecraft:crafting") && r.ings.size() == 1 && r.count >= 2) {
                for (String block : r.ings.get(0)) unpack.put(block + ">" + r.out, r.count);
            }
        }
        int pairCount = 0;
        for (Rec r : recipes) {
            if (!r.type.equals("minecraft:crafting") || r.count != 1 || r.ings.size() < 2 || excluded.contains(r.out)) continue;
            List<Set<String>> sets = new ArrayList<>();
            for (List<String> l : r.ings) sets.add(new HashSet<>(l));
            List<String> common = new ArrayList<>();
            for (String id : r.ings.get(0)) {
                if (sets.stream().allMatch(set -> set.contains(id))) common.add(id);
            }
            for (String unit : common) {
                int k = r.ings.size();
                Integer up = unpack.get(r.out + ">" + unit);
                if (unit.equals(r.out) || excluded.contains(unit) || up == null || up != k) continue;
                Root gu = find(unit);
                Root gb = find(r.out);
                if (gu.root.equals(gb.root)) break;
                // value(block) = k x value(unit)  =>  value(rootB) = k * fu / fb x value(rootU)
                parent.put(gb.root, new Link(gu.root, k * gu.f / gb.f));
                pairCount++;
                break;
            }
        }
        for (String id : parent.keySet()) {
            Root g = find(id);
            groupMembers.computeIfAbsent(g.root, root -> new ArrayList<>(List.of(root))).add(id);
        }
        groupMembers.forEach((root, ms) -> {
            for (String m : ms) factor.put(m, find(m).f);
        });
        // Groups with a fixed member (like iron ingots) take their value from it
        groupMembers.forEach((root, ms) -> {
            String anchor = null;
            for (String m : ms) {
                if (fixed.contains(m)) {
                    anchor = m;
                    break;
                }
            }
            if (anchor == null) return;
            double rootValue = value.get(anchor) / factor.get(anchor);
            for (String m : ms) {
                if (fixed.contains(m)) continue;
                value.put(m, factor.get(m) * rootValue);
                source.put(m, describeRatio(m, anchor));
                fixed.add(m);
            }
        });
        return pairCount;
    }

    // ------------------------------------------------------------------------------------------ propagation

    /** Prices items from their cheapest recipe, repeating until nothing changes. useRecipe decides which recipes this
     * pass may use, canSet which items it may price. */
    private void propagate(Predicate<Rec> useRecipe, Predicate<String> canSet) {
        int[] queue = new int[Math.max(16, recipes.size())];
        int tail = 0;
        boolean[] queued = new boolean[recipes.size()];
        for (int n = 0; n < recipes.size(); n++) {
            if (useRecipe.test(recipes.get(n))) {
                queue[tail++] = n;
                queued[n] = true;
            }
        }
        int head = 0;
        while (head < tail) {
            int n = queue[head++];
            queued[n] = false;
            Rec r = recipes.get(n);
            if (excluded.contains(r.out) || fixed.contains(r.out) || !canSet.test(r.out)) continue;
            double total = 0;
            boolean ok = true;
            for (int k = 0; k < r.ings.size() && ok; k++) {
                double best = -1;
                for (String item : r.ings.get(k)) {
                    Double v = value.get(item);
                    if (v != null && (best < 0 || v < best)) best = v;
                }
                if (best < 0) ok = false;
                else total += best;
            }
            if (!ok) continue;
            double cost = r.cooking
                    ? (total * (1 + s.foodMarkup) + s.foodLabor) / r.count
                    : (total * (1 + s.stepMarkup) + s.stepLabor) / r.count;
            Double old = value.get(r.out);
            if (old == null || cost < old * 0.999) {
                String label = replaceFirst(r.type, "minecraft:", "") + " recipe " + r.id;
                Double fOut = factor.get(r.out);
                double rootValue = fOut != null && fOut != 0 ? cost / fOut : cost;
                for (String m : membersOf(r.out)) {
                    Double fm = factor.get(m);
                    value.put(m, fm != null && fm != 0 ? fm * rootValue : cost);
                    source.put(m, m.equals(r.out) ? label : describeRatio(m, r.out) + " (" + label + ")");
                    List<Integer> users = usedBy.get(m);
                    if (users == null) continue;
                    for (int u : users) {
                        if (!queued[u] && useRecipe.test(recipes.get(u))) {
                            if (tail == queue.length) queue = java.util.Arrays.copyOf(queue, queue.length * 2);
                            queue[tail++] = u;
                            queued[u] = true;
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------------------------------ bosses

    /** Finds bosses and notes every item in their loot tables. Returns the sorted boss ids. */
    @SuppressWarnings("unchecked")
    private List<String> findBossDrops() {
        List<String> bossNames = new ArrayList<>();
        try {
            Set<String> bossIds = new LinkedHashSet<>(s.bosses);
            ResourceLocation bossTag = ResourceLocation.parse("c:bosses");
            BuiltInRegistries.ENTITY_TYPE.getTag(TagKey.create(Registries.ENTITY_TYPE, bossTag)).ifPresent(set -> {
                for (int i = 0; i < set.size(); i++) bossIds.add(BuiltInRegistries.ENTITY_TYPE.getKey(set.get(i).value()).toString());
            });
            for (ResourceLocation key : BuiltInRegistries.ENTITY_TYPE.keySet()) {
                Double health = attempt("mob health", () -> {
                    EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
                    return DefaultAttributes.hasSupplier(type)
                            ? DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) type).getBaseValue(Attributes.MAX_HEALTH)
                            : 0.0;
                });
                if (health != null && health >= s.bossMinHealth) bossIds.add(key.toString());
            }
            s.notBosses.forEach(bossIds::remove);
            bossNames.addAll(bossIds);
            bossNames.sort(Comparator.naturalOrder());

            // Read each boss's loot table (and a reward chest with the same name, like Bosses of Mass Destruction
            // uses) and note every item in it
            for (String b : bossNames) {
                readTable(ns(b), "entities/" + path(b), 0);
                readTable(ns(b), "chests/" + path(b), 0);
            }
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(LOG + "Could not read boss drops: {}", e.toString());
        }
        return bossNames;
    }

    private void readTable(String ns, String path, int depth) {
        attempt("boss loot table", () -> {
            ResourceLocation rl = ResourceLocation.tryBuild(ns, "loot_table/" + path + ".json");
            if (rl == null) return null;
            Optional<Resource> res = server.getResourceManager().getResource(rl);
            if (res.isEmpty()) return null;
            JsonElement json;
            try (Reader reader = res.get().openAsReader()) {
                json = JsonParser.parseReader(reader);
            }
            walkLoot(json, depth);
            return null;
        });
    }

    private void walkLoot(JsonElement el, int depth) {
        if (el == null) return;
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) walkLoot(e, depth);
            return;
        }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        String type = o.has("type") && o.get("type").isJsonPrimitive() ? replaceFirst(o.get("type").getAsString(), "minecraft:", "") : "";
        if (type.equals("item") && o.has("name")) bossDrops.add(o.get("name").getAsString());
        if (type.equals("tag") && o.has("name")) bossDrops.addAll(tagItems(o.get("name").getAsString()));
        if (type.equals("loot_table") && depth < 3) {
            JsonElement ref = o.has("value") ? o.get("value") : (o.has("name") ? o.get("name") : null);
            if (ref != null && ref.isJsonPrimitive()) {
                String[] parts = ref.getAsString().split(":");
                if (parts.length == 1) readTable("minecraft", parts[0], depth + 1);
                else readTable(parts[0], parts[1], depth + 1);
            }
        }
        for (Map.Entry<String, JsonElement> field : o.entrySet()) {
            JsonElement child = field.getValue();
            if (child.isJsonArray() || child.isJsonObject()) walkLoot(child, depth);
        }
    }

    // ------------------------------------------------------------------------------------------ rarity fallback

    private void giveFallback(String id, int[] counts) {
        String rarity = attempt("rarity", () -> new ItemStack(item(id)).getRarity().name());
        if (rarity == null) rarity = "COMMON";
        int tier = Math.max(0, RARITIES.indexOf(rarity));
        List<String> notes = new ArrayList<>();
        if (maxStackOf(id) <= 1 && !craftable.contains(id)) {
            tier += s.unstackableBonus;
            notes.add("+" + s.unstackableBonus + " unstackable");
        }
        if (bossDrops.contains(id)) {
            tier += s.bossDropBonus;
            notes.add("+" + s.bossDropBonus + " boss drop");
        }
        if (s.fallbackTiers.length == 0) return;
        tier = Math.max(0, Math.min(tier, s.fallbackTiers.length - 1));
        value.put(id, s.fallbackTiers[tier]);
        source.put(id, "rarity fallback (" + rarity.toLowerCase(Locale.ROOT) + (notes.isEmpty() ? "" : " " + String.join(" ", notes)) + ")");
        counts[0]++;
        if (!notes.isEmpty()) counts[1]++;
    }

    // ------------------------------------------------------------------------------------------ villagers

    /**
     * Rolls sample villagers of every job and level (and wandering traders), reads their trades, and caps every
     * item at villager_cap of the cheapest villager price. Returns how many prices were capped.
     */
    private int readVillagerPrices() {
        int capped = 0;
        try {
            ServerLevel level = server.overworld();
            int[] counts = new int[2]; // traders, offers
            for (ResourceLocation pkey : BuiltInRegistries.VILLAGER_PROFESSION.keySet()) {
                if (s.skipProfessions.contains(pkey.toString())) continue;
                VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(pkey);
                for (int lvl = 1; lvl <= 5; lvl++) {
                    final int villagerLevel = lvl;
                    for (int n = 0; n < s.villagerSamples; n++) {
                        Villager v = attempt("villager setup", () -> {
                            Villager villager = EntityType.VILLAGER.create(level);
                            if (villager == null) return null;
                            villager.setVillagerData(villager.getVillagerData().setProfession(profession).setLevel(villagerLevel));
                            return villager;
                        });
                        if (v != null) readOffers(v, counts);
                    }
                }
            }
            for (int n = 0; n < s.villagerSamples * 3; n++) {
                WanderingTrader w = attempt("wandering trader setup", () -> EntityType.WANDERING_TRADER.create(level));
                if (w != null) readOffers(w, counts);
            }
            for (Map.Entry<String, Double> e : villagerUnit.entrySet()) {
                String id = e.getKey();
                double cap = e.getValue() * s.villagerCap;
                Double v = value.get(id);
                if (v != null && v > cap) {
                    value.put(id, cap);
                    source.put(id, "capped below villager price");
                    capped++;
                }
            }
            log("Villager prices: " + counts[0] + " sample traders, " + counts[1] + " offers, "
                    + villagerUnit.size() + " items villagers sell, " + capped + " capped");
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(LOG + "Could not read villager prices: {}", e.toString());
        }
        return capped;
    }

    private void readOffers(AbstractVillager trader, int[] counts) {
        MerchantOffers offers = attempt("villager trades", trader::getOffers);
        if (offers == null) return;
        counts[0]++;
        for (MerchantOffer offer : offers) {
            counts[1]++;
            attempt("villager offer", () -> {
                ItemCost a = offer.getItemCostA();
                Optional<ItemCost> b = offer.getItemCostB();
                double e = VillagerCoins.emeraldsIn(a.item().value(), a.count(), s);
                if (b.isPresent()) e += VillagerCoins.emeraldsIn(b.get().item().value(), b.get().count(), s);
                ItemStack res = offer.getResult();
                if (e <= 0 || VillagerCoins.emeraldsIn(res.getItem(), res.getCount(), s) > 0) return null;
                String rid = idOf(res.getItem());
                // Villagers charge boosted items the same multiple more
                Double mult = s.priceMultipliers.get(rid);
                double spurs = Math.max(1, Math.round(s.scale * Math.pow(e, s.curve))) * (mult == null || mult == 0 ? 1 : mult);
                double unit = spurs / res.getCount();
                Double old = villagerUnit.get(rid);
                if (old == null || unit < old) villagerUnit.put(rid, unit);
                return null;
            });
        }
    }

    // ------------------------------------------------------------------------------------------ trades

    /** Anything worth a spur or more sells one at a time, paid in the largest coin that fits. Cheaper things sell in
     * batches for 1 spur. Unstackable items always sell one at a time. */
    private Sale buildTrade(double v, int maxStack) {
        if (v < 1) {
            if (maxStack <= 1) return new Sale(1, "numismatics:spur", 1);
            return new Sale((int) Math.min(s.maxBatch, Math.max(1, Math.round(1 / v))), "numismatics:spur", 1);
        }
        String bestCoin = null;
        int bestM = 0;
        double bestErr = 0;
        for (Coins.Coin c : Coins.LARGEST_FIRST) {
            long m = Math.round(v / c.value());
            if (m < 1 || m > 64) continue;
            double err = Math.abs(m * c.value() - v) / v;
            if (err <= s.rounding) return new Sale(1, c.id(), (int) m);
            if (bestCoin == null || err < bestErr) {
                bestCoin = c.id();
                bestM = (int) m;
                bestErr = err;
            }
        }
        return bestCoin != null ? new Sale(1, bestCoin, bestM) : new Sale(1, "numismatics:sun", 64);
    }

    /** Like buildTrade, but whole-spur amounts are paid exactly when one coin type can do it. */
    private Sale exactTrade(double v, int maxStack) {
        long whole = Math.round(v);
        if (v >= 1 && Math.abs(whole - v) < 1e-6) {
            for (Coins.Coin c : Coins.LARGEST_FIRST) {
                if (whole % c.value() == 0 && whole / c.value() <= 64) return new Sale(1, c.id(), (int) (whole / c.value()));
            }
        }
        return buildTrade(v, maxStack);
    }

    /** Storage groups pay consistently: the smallest member that sells one at a time (usually the ingot) sets the
     * rate, bigger members pay exactly that many times more, smaller ones sell in batches. */
    private void storageGroupRates(Map<String, Sale> trades) {
        groupMembers.forEach((root, members) -> {
            List<String> ms = new ArrayList<>();
            for (String m : members) if (trades.containsKey(m)) ms.add(m);
            if (ms.size() < 2) return;
            List<String> sorted = new ArrayList<>(ms);
            sorted.sort(Comparator.comparingDouble(factor::get));
            String anchor = null;
            for (String m : sorted) {
                if (trades.get(m).count == 1) {
                    anchor = m;
                    break;
                }
            }
            if (anchor == null) anchor = sorted.get(sorted.size() - 1);
            // If a villager sells any member cheaply, the whole group's rate is capped to match
            Double rateCap = null;
            for (String m : ms) {
                Double unit = villagerUnit.get(m);
                if (unit == null) continue;
                double c = unit * s.villagerCap / factor.get(m);
                if (rateCap == null || c < rateCap) rateCap = c;
            }
            double fa = factor.get(anchor);
            if (rateCap != null && trades.get(anchor).payout() > rateCap * fa * 1.0001) {
                trades.put(anchor, buildTrade(rateCap * fa, maxStackOf(anchor)));
                value.put(anchor, rateCap * fa);
                source.put(anchor, "capped below villager price");
            }
            Sale ta = trades.get(anchor);
            double pa = ta.payout();
            for (String m : ms) {
                if (m.equals(anchor)) continue;
                double ratio = factor.get(m) / fa;
                if (ratio > 1) {
                    trades.put(m, exactTrade(pa * ratio, maxStackOf(m)));
                    value.put(m, pa * ratio);
                } else {
                    long n = Math.round(1 / ratio);
                    if (ta.count * n > s.maxBatch) continue;
                    trades.put(m, new Sale((int) (ta.count * n), ta.coin, ta.coinCount));
                    value.put(m, pa / n);
                }
                source.put(m, describeRatio(m, anchor) + " (packs/unpacks)");
            }
        });
    }

    /** Rechiseled and Chipped variants sell for the same as the block they come from (done last, so they copy the
     * block's final price). Rechiseled slabs are worth half a block and stairs a full block. */
    private int chiselVariants(Map<String, Sale> trades) {
        int chiselCount = 0;
        try {
            Map<String, List<String>> byPath = new HashMap<>();
            for (String id : allIds) {
                if (s.chiselMods.contains(ns(id))) continue;
                byPath.computeIfAbsent(path(id), k -> new ArrayList<>()).add(id);
            }
            record Group(String path, List<String> members) {}
            List<Group> groups = new ArrayList<>();
            Set<String> halfBlocks = new HashSet<>();
            List<Pair<TagKey<Item>, HolderSet.Named<Item>>> tagList = BuiltInRegistries.ITEM.getTags().toList();
            for (Pair<TagKey<Item>, HolderSet.Named<Item>> pair : tagList) {
                ResourceLocation tagId = pair.getFirst().location();
                String tns = tagId.getNamespace();
                String tpath = tagId.getPath();
                if (!s.chiselMods.contains(tns)) continue;
                HolderSet.Named<Item> set = pair.getSecond();
                List<String> members = new ArrayList<>();
                for (int k = 0; k < set.size(); k++) members.add(idOf(set.get(k).value()));
                if (tns.equals("rechiseled") && tpath.endsWith("_slabs")) halfBlocks.addAll(members);
                else if (!(tns.equals("rechiseled") && tpath.endsWith("_stairs"))) groups.add(new Group(tpath, members));
            }
            for (Group g : groups) {
                // The original block: Chipped lists it in the group; for Rechiseled it has the group's name
                List<String> outside = new ArrayList<>();
                for (String m : g.members) if (!s.chiselMods.contains(ns(m))) outside.add(m);
                String origin = null;
                for (String m : outside) {
                    if (path(m).equals(g.path)) {
                        origin = m;
                        break;
                    }
                }
                if (origin == null && !outside.isEmpty()) origin = outside.get(0);
                if (origin == null) {
                    List<String> named = byPath.getOrDefault(g.path, List.of());
                    for (String m : named) {
                        if (ns(m).equals("minecraft")) {
                            origin = m;
                            break;
                        }
                    }
                    if (origin == null && !named.isEmpty()) origin = named.get(0);
                }
                if (origin == null || !trades.containsKey(origin)) continue;
                for (String m : g.members) {
                    if (!s.chiselMods.contains(ns(m)) || excluded.contains(m) || !exists.contains(m)) continue;
                    if (halfBlocks.contains(m)) {
                        // Half the block's pay; if that isn't a whole amount, 2 slabs pay exactly what 1 block pays
                        Sale to = trades.get(origin);
                        double v = to.payout() / 2;
                        Sale half = exactTrade(v, maxStackOf(m));
                        trades.put(m, Math.abs(half.payout() - v) < 1e-6 ? half : new Sale(to.count * 2, to.coin, to.coinCount));
                        value.put(m, v);
                        source.put(m, "chiseled from " + origin + " (slab, half)");
                    } else {
                        trades.put(m, trades.get(origin));
                        value.put(m, value.get(origin));
                        source.put(m, "chiseled from " + origin);
                    }
                    chiselCount++;
                }
            }
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(LOG + "Could not read Rechiseled/Chipped groups: {}", e.toString());
        }
        return chiselCount;
    }

    /** Explains the items in the explain setting: every recipe that makes them and what each ingredient is worth. */
    private void explain(Map<String, Sale> trades) {
        Map<String, List<Rec>> makers = new HashMap<>();
        for (Rec r : recipes) makers.computeIfAbsent(r.out, k -> new ArrayList<>()).add(r);
        for (String id : s.explain) {
            if (!exists.contains(id)) continue;
            Sale t = trades.get(id);
            log("EXPLAIN " + id + " = " + fmt(value.get(id)) + " spurs, sells "
                    + (t != null ? t.count + " for " + t.coinCount + " " + t.coin : "never")
                    + ", from: " + Objects.requireNonNullElse(source.get(id), "nothing"));
            List<Rec> list = makers.getOrDefault(id, List.of());
            for (Rec r : list.subList(0, Math.min(6, list.size()))) {
                List<String> parts = new ArrayList<>();
                for (List<String> items : r.ings) {
                    String best = null;
                    for (String i : items) {
                        Double v = value.get(i);
                        if (v != null && (best == null || v < value.get(best))) best = i;
                    }
                    parts.add(best != null ? best + "=" + fmt(value.get(best)) : items.get(0) + "=unpriced");
                }
                log("    " + r.type + " " + r.id + (r.primary ? "" : " [machine]") + ": " + String.join(", ", parts) + " -> makes " + r.count);
            }
        }
    }

    private static String fmt(Double v) {
        return v == null ? "unpriced" : jsNum(Math.round(v * 1000) / 1000.0);
    }
}
