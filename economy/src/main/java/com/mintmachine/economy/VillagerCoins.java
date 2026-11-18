package com.mintmachine.economy;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleUnaryOperator;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Every villager and wandering trader trades in Numismatics coins, priced across the whole coin ladder. Covers buying
 * and selling, vanilla and modded villagers, and Easy Villagers trader blocks. Each trade is converted once, the
 * first time a player opens that villager's trades. (Port of villager_coins.js; the villagers' saved data keys are
 * the same, so villagers it already converted are left as they are.)
 */
final class VillagerCoins {
    private VillagerCoins() {}

    private static final String LOG = "[villager_coins] ";

    static void register(IEventBus bus) {
        bus.addListener(VillagerCoins::onEntityInteract);
        bus.addListener(VillagerCoins::onContainerOpen);
        bus.addListener(VillagerCoins::onRightClickBlock);
    }

    private static void log(String msg) {
        if (EconomySettings.get().villagerDebug) MintMachineEconomy.LOG.info(LOG + msg);
    }

    // -------------------------------------------------------------------------------------------- pricing

    /** How many emeralds a stack is worth (0 means it isn't currency). Coins in a trade not yet converted were put
     * there by Villager Currency. */
    static double emeraldsIn(Item item, int count, EconomySettings s) {
        String id = Coins.idOf(item);
        if (id.equals("minecraft:emerald")) return count;
        if (id.equals("minecraft:emerald_block")) return count * 9.0;
        int coin = Coins.valueOf(id);
        if (coin > 0) return count * (double) coin / s.villagerCurrencySpursPerEmerald;
        return 0;
    }

    static double priceInSpurs(double emeralds, EconomySettings s) {
        return Math.max(1, Math.round(s.scale * Math.pow(emeralds, s.curve)));
    }

    private static double boostFor(Item item, EconomySettings s) {
        Double b = s.priceMultipliers.get(Coins.idOf(item));
        return b == null || b == 0 ? 1 : b;
    }

    private record Pick(Coins.Coin coin, int n, double err) {}

    /** Best single stack (1-64 coins) of the largest coin that lands within the rounding of the target. */
    private static Pick pick(double target, double maxValue, double base, double rounding) {
        Pick best = null;
        for (Coins.Coin coin : Coins.LARGEST_FIRST) {
            if (coin.value() > maxValue) continue;
            long n = Math.round(target / coin.value());
            if (n < 1 || n > 64) continue;
            double err = Math.abs(n * coin.value() - target) / base;
            if (err <= rounding) return new Pick(coin, (int) n, err);
            if (best == null || err < best.err) best = new Pick(coin, (int) n, err);
        }
        return best != null ? best : new Pick(Coins.LARGEST_FIRST.get(0), 64, 1);
    }

    /** Coins for a price, using one trade slot or (if needed and available) two. */
    private static List<Pick> coinsFor(double spurs, int slots, double rounding) {
        Pick one = pick(spurs, 4096, spurs, rounding);
        if (slots == 1 || one.err <= rounding) return List.of(one);

        Coins.Coin big = null;
        for (Coins.Coin c : Coins.LARGEST_FIRST) {
            if (c.value() <= spurs) {
                big = c;
                break;
            }
        }
        if (big == null) return List.of(one);
        int n = (int) Math.min(64, Math.floor(spurs / big.value()));
        double rest = spurs - n * big.value();
        if (rest / spurs <= rounding) return List.of(new Pick(big, n, 0));

        Pick small = pick(rest, big.value() - 1, spurs, rounding);
        if ((double) small.n * small.coin.value() >= big.value() && n < 64) return List.of(new Pick(big, n + 1, 0));
        return List.of(new Pick(big, n, 0), small);
    }

    private static ItemCost cost(Pick p) {
        return new ItemCost(p.coin.item(), p.n);
    }

    private static String describe(MerchantOffer offer) {
        ItemCost a = offer.getItemCostA();
        Optional<ItemCost> b = offer.getItemCostB();
        StringBuilder text = new StringBuilder(a.count() + "x " + Coins.idOf(a.item().value()));
        b.ifPresent(cb -> text.append(" + ").append(cb.count()).append("x ").append(Coins.idOf(cb.item().value())));
        ItemStack r = offer.getResult();
        return text + " -> " + r.getCount() + "x " + Coins.idOf(r.getItem());
    }

    private static MerchantOffer rebuild(MerchantOffer offer, ItemCost a, Optional<ItemCost> b, ItemStack result) {
        return new MerchantOffer(a, b, result, offer.getUses(), offer.getMaxUses(), offer.getXp(),
                offer.getPriceMultiplier(), offer.getDemand());
    }

    /** A coin version of the trade, or null if it has no currency in it. */
    private static MerchantOffer convertOffer(MerchantOffer offer, EconomySettings s) {
        ItemCost a = offer.getItemCostA();
        Optional<ItemCost> bOpt = offer.getItemCostB();
        ItemCost b = bOpt.orElse(null);
        ItemStack result = offer.getResult();

        double eA = emeraldsIn(a.item().value(), a.count(), s);
        double eB = b != null ? emeraldsIn(b.item().value(), b.count(), s) : 0;
        double eR = emeraldsIn(result.getItem(), result.getCount(), s);
        if (eA == 0 && eB == 0 && eR == 0) return null;
        if ((eA > 0 || eB > 0) && eR > 0) return null; // currency for currency, leave it alone

        ItemCost newA = a;
        Optional<ItemCost> newB = bOpt;
        ItemStack newResult = result;
        double r = s.villagerRounding;

        double boost = eR > 0 ? 1 : boostFor(result.getItem(), s); // only when the villager is selling
        if (eA > 0 && (eB > 0 || b == null)) {
            List<Pick> coins = coinsFor(priceInSpurs(eA + eB, s) * boost, 2, r);
            newA = cost(coins.get(0));
            newB = coins.size() > 1 ? Optional.of(cost(coins.get(1))) : Optional.empty();
        } else if (eA > 0) {
            newA = cost(coinsFor(priceInSpurs(eA, s) * boost, 1, r).get(0)); // second slot holds an item, e.g. a book
        } else if (eB > 0) {
            newB = Optional.of(cost(coinsFor(priceInSpurs(eB, s) * boost, 1, r).get(0)));
        }
        if (eR > 0) {
            Pick c = coinsFor(priceInSpurs(eR, s), 1, r).get(0);
            newResult = new ItemStack(c.coin.item(), c.n);
        }
        return rebuild(offer, newA, newB, newResult);
    }

    private static double spursIn(Item item, int count) {
        return (double) Coins.valueOf(Coins.idOf(item)) * count;
    }

    /** Rebuilds a coin trade with every coin amount passed through f(spurs). */
    private static MerchantOffer remapOffer(MerchantOffer offer, DoubleUnaryOperator f, EconomySettings s) {
        ItemCost a = offer.getItemCostA();
        Optional<ItemCost> bOpt = offer.getItemCostB();
        ItemCost b = bOpt.orElse(null);
        ItemStack result = offer.getResult();

        double sA = spursIn(a.item().value(), a.count());
        double sB = b != null ? spursIn(b.item().value(), b.count()) : 0;
        double sR = spursIn(result.getItem(), result.getCount());
        if (sA == 0 && sB == 0 && sR == 0) return null;

        ItemCost newA = a;
        Optional<ItemCost> newB = bOpt;
        ItemStack newResult = result;
        double r = s.villagerRounding;
        if (sA > 0 && (sB > 0 || b == null)) {
            List<Pick> coins = coinsFor(f.applyAsDouble(sA + sB), 2, r);
            newA = cost(coins.get(0));
            newB = coins.size() > 1 ? Optional.of(cost(coins.get(1))) : Optional.empty();
        } else if (sA > 0) {
            newA = cost(coinsFor(f.applyAsDouble(sA), 1, r).get(0));
        } else if (sB > 0) {
            newB = Optional.of(cost(coinsFor(f.applyAsDouble(sB), 1, r).get(0)));
        }
        if (sR > 0) {
            Pick c = coinsFor(f.applyAsDouble(sR), 1, r).get(0);
            newResult = new ItemStack(c.coin.item(), c.n);
        }
        return rebuild(offer, newA, newB, newResult);
    }

    /** The price boosts as JSON text, written the way the KubeJS script wrote them (8, not 8.0). */
    static String boostKey(Map<String, Double> boosts) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Double> e : boosts.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey().replace("\\", "\\\\").replace("\"", "\\\"")).append("\":");
            double v = e.getValue();
            if (v == Math.rint(v) && Math.abs(v) < 1e15) sb.append((long) v);
            else sb.append(v);
        }
        return sb.append('}').toString();
    }

    private static Map<String, Double> parseBoosts(String text) {
        Map<String, Double> out = new HashMap<>();
        if (text == null || text.isEmpty()) return out;
        try {
            JsonElement json = JsonParser.parseString(text);
            if (json.isJsonObject()) {
                json.getAsJsonObject().entrySet().forEach(e -> {
                    try {
                        out.put(e.getKey(), e.getValue().getAsDouble());
                    } catch (Exception ignored) {
                        // not a number
                    }
                });
            }
        } catch (Exception ignored) {
            // unreadable: treat as no boosts
        }
        return out;
    }

    private static double orOne(Double d) {
        return d == null || d == 0 ? 1 : d;
    }

    private static String typeOf(Entity entity) {
        return EntityType.getKey(entity.getType()).toString();
    }

    // -------------------------------------------------------------------------------------------- converting

    /**
     * Converts any of a merchant's trades that haven't been converted yet. Remembers how many trades are done (and
     * for which job) in the entity's data, so nothing is converted twice.
     */
    static boolean coinify(Entity entity, String source) {
        if (!(entity instanceof Merchant merchant) || entity.level().isClientSide()) return false;
        if (!Coins.present()) return false;
        EconomySettings s = EconomySettings.get();
        MerchantOffers offers = merchant.getOffers();
        if (offers == null || offers.isEmpty()) return false;

        CompoundTag data = entity.getPersistentData();
        String job = entity instanceof VillagerDataHolder holder
                ? String.valueOf(BuiltInRegistries.VILLAGER_PROFESSION.getKey(holder.getVillagerData().getProfession()))
                : "trader";
        int done = data.getString("coin_trades_v3_job").equals(job) ? data.getInt("coin_trades_v3_done") : 0;
        int changed = 0;

        // Trades converted with the old curve get re-priced once
        String oldJob = data.getString("coin_trades_v2_job");
        if (data.getString("coin_trades_v3_job").isEmpty() && oldJob.equals(job)) {
            int old = Math.min(data.getInt("coin_trades_v2_done"), offers.size());
            DoubleUnaryOperator fromOld = spurs -> priceInSpurs(Math.pow(spurs / s.oldScale, 1 / s.oldCurve), s);
            for (int i = 0; i < old; i++) {
                try {
                    String before = describe(offers.get(i));
                    MerchantOffer repriced = remapOffer(offers.get(i), fromOld, s);
                    if (repriced != null) {
                        offers.set(i, repriced);
                        changed++;
                        log("  re-priced " + before + "  =>  " + describe(repriced));
                    }
                } catch (Exception e) {
                    MintMachineEconomy.LOG.error(LOG + "Could not re-price trade {} of {}: {}", i, typeOf(entity), e.toString());
                }
            }
            done = old;
        }
        if (done > offers.size()) done = 0;

        // Price boosts changed since this villager's trades were converted: rescale those trades
        String boostKey = boostKey(s.priceMultipliers);
        String oldBoostText = data.getString("coin_boosts");
        if (!oldBoostText.equals(boostKey)) {
            Map<String, Double> oldBoosts = parseBoosts(oldBoostText);
            for (int i = 0; i < done; i++) {
                try {
                    MerchantOffer offer = offers.get(i);
                    String rid = Coins.idOf(offer.getResult().getItem());
                    if (Coins.valueOf(rid) > 0) continue; // the villager is buying, not selling
                    double ratio = orOne(s.priceMultipliers.get(rid)) / orOne(oldBoosts.get(rid));
                    if (Math.abs(ratio - 1) < 1e-9) continue;
                    String before = describe(offer);
                    MerchantOffer rescaled = remapOffer(offer, spurs -> Math.max(1, Math.round(spurs * ratio)), s);
                    if (rescaled != null) {
                        offers.set(i, rescaled);
                        changed++;
                        log("  boost x" + ratio + ": " + before + "  =>  " + describe(rescaled));
                    }
                } catch (Exception e) {
                    MintMachineEconomy.LOG.error(LOG + "Could not rescale trade {} of {}: {}", i, typeOf(entity), e.toString());
                }
            }
        }

        for (int i = done; i < offers.size(); i++) {
            try {
                String before = describe(offers.get(i));
                MerchantOffer converted = convertOffer(offers.get(i), s);
                if (converted != null) {
                    offers.set(i, converted);
                    changed++;
                    log("  " + before + "  =>  " + describe(converted));
                } else {
                    log("  " + before + "  (no currency, left as is)");
                }
            } catch (Exception e) {
                MintMachineEconomy.LOG.error(LOG + "Could not convert trade {} of {}: {}", i, typeOf(entity), e.toString());
            }
        }
        log(typeOf(entity) + " (" + job + ", via " + source + "): " + offers.size() + " trades, "
                + (offers.size() - done) + " new, " + changed + " converted");

        data.putString("coin_trades_v3_job", job);
        data.putInt("coin_trades_v3_done", offers.size());
        data.putString("coin_boosts", boostKey);
        return true;
    }

    // -------------------------------------------------------------------------------------------- events

    // Villagers, wandering traders and other trading mobs: convert right before the trade screen opens
    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide()) return;
        coinify(event.getTarget(), "right-click");
    }

    // Backup: when any trade screen opens, find the mob trading with this player. This still works if another mod
    // handles the right-click first. (The offers are sent to the player after this event.)
    private static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof MerchantMenu) || !(event.getEntity() instanceof ServerPlayer player)) return;
        for (Entity e : player.level().getEntities(player, player.getBoundingBox().inflate(16))) {
            if (e instanceof Merchant m && player.equals(m.getTradingPlayer())) {
                coinify(e, "trade screen");
                return;
            }
        }
        log("Trade screen opened, but no trading mob was found near " + player.getGameProfile().getName());
    }

    // Easy Villagers trader and auto trader blocks keep their villager inside the block
    private static final Map<Class<?>, Optional<Method[]>> VILLAGER_BLOCKS = new ConcurrentHashMap<>();

    private static Optional<Method[]> villagerMethods(Class<?> type) {
        return VILLAGER_BLOCKS.computeIfAbsent(type, t -> {
            try {
                Method get = t.getMethod("getVillagerEntity");
                Method save = null;
                try {
                    save = t.getMethod("saveVillagerEntity");
                } catch (NoSuchMethodException ignored) {
                    // optional
                }
                return Optional.of(new Method[]{get, save});
            } catch (NoSuchMethodException | SecurityException e) {
                return Optional.empty();
            }
        });
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) return;
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        if (be == null) return;
        Optional<Method[]> methods = villagerMethods(be.getClass());
        if (methods.isEmpty()) return;
        try {
            Object villager = methods.get()[0].invoke(be);
            if (villager instanceof Entity entity && coinify(entity, "trader block")) {
                if (methods.get()[1] != null) methods.get()[1].invoke(be);
                be.setChanged();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            MintMachineEconomy.LOG.error(LOG + "Could not convert the trades of a villager block at {}: {}", event.getPos(), e.toString());
        }
    }
}
