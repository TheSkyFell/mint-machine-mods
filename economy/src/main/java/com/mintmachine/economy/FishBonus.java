package com.mintmachine.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import com.wdiscute.starcatcher.data.CaughtFishInfo;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import tfar.shippingbin.ShippingBin;
import tfar.shippingbin.ShippingBinConfig;
import tfar.shippingbin.init.ModAttributes;
import tfar.shippingbin.inventory.CommonHandler;
import tfar.shippingbin.level.ShippingBinInventories;
import tfar.shippingbin.trades.Trade;

/**
 * Golden and big Starcatcher fish sell for more in the Shipping Bin (port of shipping_fish_bonus.js).
 * <p>
 * The Shipping Bin prices by item alone, so it can't tell a golden or record-size fish from any other catch of the
 * same kind. This steps in just before each sale: it sells those fish itself, for the Shipping Bin's price for that
 * kind of fish times a bonus, and puts the coins in the bin's output like a normal sale. Golden fish: x10. Bigger than
 * average (top 50% by size): from x1 at the middle up to x2 for the very biggest. The player's Shipping Bin sell
 * multiplier applies on top. It runs at the same two moments the Shipping Bin sells, on the same events at a higher
 * priority, so it always goes first. If the output can't hold the coins, the fish stays in the bin and the normal
 * sale takes it at the normal price. Only active when Starcatcher is installed.
 */
final class FishBonus {
    private FishBonus() {}

    private static final String LOG = "[shipping_fish_bonus] ";

    private static DataComponentType<?> fishInfo;
    private static boolean broken;

    static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGHEST, FishBonus::onServerTick);
        bus.addListener(EventPriority.HIGHEST, FishBonus::onSleepFinished);
    }

    private static boolean ready() {
        if (fishInfo != null) return true;
        if (broken) return false;
        fishInfo = BuiltInRegistries.DATA_COMPONENT_TYPE.get(ResourceLocation.parse("starcatcher:caught_fish_info"));
        if (fishInfo == null) {
            broken = true;
            MintMachineEconomy.LOG.error(LOG + "Turned off: no starcatcher:caught_fish_info component");
            return false;
        }
        return true;
    }

    /** The bonus for one caught fish. */
    private static double bonusOf(CaughtFishInfo info) {
        if (info == null) return 1;
        EconomySettings s = EconomySettings.get();
        if (info.golden()) return s.goldenMultiplier;
        double p = info.percentile(); // 0 = the biggest ever, 100 = the smallest
        if (p < 50) return 1 + (50 - p) / 50 * (s.biggestMultiplier - 1);
        return 1;
    }

    private static String labelOf(CaughtFishInfo info, String name) {
        if (info.golden()) return "golden " + name;
        return name + " (top " + (int) Math.max(1, Math.ceil(info.percentile())) + "%)";
    }

    /** What the Shipping Bin pays for one of this item, in spurs (0 if it can't be shipped). */
    private static double spursEach(Map<ResourceLocation, Trade> trades, String id) {
        Trade t = trades.get(ShippingBinBridge.tradeId(id));
        if (t == null) return 0;
        ItemStack out = t.output();
        return (double) Coins.valueOf(Coins.idOf(out.getItem())) * out.getCount() / t.input().count();
    }

    private record CoinCount(Coins.Coin coin, int n) {}

    /** Splits spurs into coins, largest first. */
    private static List<CoinCount> toCoins(long spurs) {
        long left = spurs;
        List<CoinCount> list = new ArrayList<>();
        for (Coins.Coin c : Coins.LARGEST_FIRST) {
            long n = left / c.value();
            if (n > 0) {
                list.add(new CoinCount(c, (int) n));
                left -= n * c.value();
            }
        }
        return list;
    }

    private static String coinText(List<CoinCount> list) {
        List<String> parts = new ArrayList<>();
        for (CoinCount c : list) parts.add(c.n + " " + (c.n == 1 ? c.coin.singular() : c.coin.plural()));
        return String.join(", ", parts);
    }

    /** Can the output take all these coins at once? Counts room in matching stacks, then empty slots. */
    private static boolean fits(CommonHandler output, List<CoinCount> list) {
        int slots = output.$getSlotCount();
        int free = 0;
        for (int i = 0; i < slots; i++) if (output.$getStack(i).isEmpty()) free++;
        int needed = 0;
        for (CoinCount c : list) {
            ItemStack coin = new ItemStack(c.coin.item(), 1);
            int left = c.n;
            for (int i = 0; i < slots && left > 0; i++) {
                ItemStack s = output.$getStack(i);
                if (s.isEmpty() || !ItemStack.isSameItemSameComponents(s, coin)) continue;
                int max = Math.min(output.$getMaxStackSize(i), s.getMaxStackSize());
                left -= Math.max(0, max - s.getCount());
            }
            if (left > 0) needed += (int) Math.ceil(left / (double) Math.min(coin.getMaxStackSize(), output.$getMaxStackSize(0)));
        }
        return needed <= free;
    }

    private static void sellPrizeFish(MinecraftServer server) {
        if (!ready()) return;
        Map<ResourceLocation, Trade> trades = ShippingBin.getTradeManager().getTrades();
        for (Map.Entry<UUID, Pair<CommonHandler, CommonHandler>> entry : ShippingBinInventories.getOrCreateInstance(server).getHandlerMap().entrySet()) {
            CommonHandler input = entry.getValue().getKey();
            CommonHandler output = entry.getValue().getValue();
            if (input.isEmpty()) continue;
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            double playerMultiplier = 1;
            try {
                if (player != null) playerMultiplier = player.getAttribute(ModAttributes.SELL_MULTIPLIER).getValue();
            } catch (Exception ignored) {
                // no multiplier
            }

            List<String> sold = new ArrayList<>();
            long total = 0;
            for (int i = 0; i < input.$getSlotCount(); i++) {
                ItemStack stack = input.$getStack(i);
                if (stack.isEmpty()) continue;
                Object raw = stack.get(fishInfo);
                CaughtFishInfo info = raw instanceof CaughtFishInfo f ? f : null;
                double bonus = bonusOf(info);
                if (bonus <= 1) continue;
                double each = spursEach(trades, Coins.idOf(stack.getItem()));
                if (each <= 0) continue;
                int count = stack.getCount();
                long pay = Math.round(each * bonus * playerMultiplier * count);
                if (pay < 1) continue;
                List<CoinCount> coins = toCoins(pay);
                if (!fits(output, coins)) break; // output full: the rest goes to the normal sale
                String name = stack.getHoverName().getString();
                input.$extractStack(i, count, false);
                for (CoinCount c : coins) output.$slotlessInsertStack(new ItemStack(c.coin.item(), c.n), c.n, false);
                total += pay;
                sold.add((count > 1 ? count + " " : "") + labelOf(info, name));
            }

            if (!sold.isEmpty()) {
                MintMachineEconomy.LOG.info(LOG + entry.getKey() + ": " + String.join(", ", sold) + " for " + total + " spurs");
                if (player != null) {
                    player.sendSystemMessage(Component.literal("§6Shipping Bin:§r sold " + String.join(", ", sold)
                            + " for §e" + coinText(toCoins(total)) + "§r"));
                }
            }
        }
    }

    private static void run(MinecraftServer server) {
        try {
            sellPrizeFish(server);
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(LOG + "Prize fish sale failed, the Shipping Bin will sell them at the normal price: {}", e.toString());
        }
    }

    // The Shipping Bin's own daily sale: ServerTickEvent.Pre, when overworld time hits its sell time
    private static void onServerTick(ServerTickEvent.Pre event) {
        if (broken) return;
        MinecraftServer server = event.getServer();
        long interval = ShippingBinConfig.Server.SELLING_INTERVAL.get();
        long offset = ShippingBinConfig.Server.SELLING_INTERVAL_OFFSET.get();
        if (interval <= 0) return;
        if (server.overworld().getDayTime() % interval == offset) run(server);
    }

    // Sleeping through the sell time: the Shipping Bin sells when the skip passes its next sell time
    private static void onSleepFinished(SleepFinishedTimeEvent event) {
        if (broken || !(event.getLevel() instanceof ServerLevel level)) return;
        long interval = ShippingBinConfig.Server.SELLING_INTERVAL.get();
        long next = ShippingBinConfig.Server.SELLING_INTERVAL_OFFSET.get();
        long now = level.getDayTime();
        if (interval <= 0) return;
        while (next < now) next += interval;
        if (next < event.getNewTime()) run(level.getServer());
    }
}
