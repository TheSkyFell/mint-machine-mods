package com.mintmachine.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import tfar.shippingbin.IngredientAndCount;
import tfar.shippingbin.ShippingBin;
import tfar.shippingbin.client.ModClient;
import tfar.shippingbin.trades.Trade;
import tfar.shippingbin.trades.TradeManager;

/**
 * The player's side of {@link PriceSync} (only loaded in the player's game). When every part of the server's prices
 * has arrived, they replace Shipping Bin's client copy of the trade list, together with its list of accepted items.
 * <p>
 * The price details (value in spurs and where the price came from) are kept here for the modpack's tooltip script
 * (kubejs/client_scripts/shipping_tooltips.js), through {@link #hasPrices()}, {@link #value(String)} and
 * {@link #source(String)}. They are cleared when the player leaves the world.
 */
public final class ClientPrices {
    private ClientPrices() {}

    private record Detail(double value, String source) {}

    private static Map<String, Detail> details = Map.of();
    private static boolean received;
    private static int pendingBatch = -1;
    private static final List<PricesPayload.Entry> pending = new ArrayList<>();

    static void register(IEventBus game) {
        game.addListener(ClientPrices::onLoggingOut);
    }

    /** True once the server's prices have arrived in this world. */
    public static boolean hasPrices() {
        return received;
    }

    /** The item's value in spurs, or NaN if the server sent no price for it. */
    public static double value(String itemId) {
        Detail d = details.get(itemId);
        return d == null ? Double.NaN : d.value;
    }

    /** Where the item's price came from, or "" if the server sent no price for it. */
    public static String source(String itemId) {
        Detail d = details.get(itemId);
        return d == null ? "" : d.source;
    }

    static void receive(PricesPayload payload) {
        if (payload.part() == 0) {
            pending.clear();
            pendingBatch = payload.batch();
        } else if (payload.batch() != pendingBatch) {
            return; // a part of an older sending
        }
        pending.addAll(payload.entries());
        if (payload.part() == payload.parts() - 1) {
            List<PricesPayload.Entry> all = new ArrayList<>(pending);
            pending.clear();
            pendingBatch = -1;
            apply(all);
        }
    }

    private static void apply(List<PricesPayload.Entry> entries) {
        Map<ResourceLocation, Trade> trades = new LinkedHashMap<>();
        Map<String, Detail> newDetails = new HashMap<>();
        for (PricesPayload.Entry e : entries) {
            String id = BuiltInRegistries.ITEM.getKey(e.item()).toString();
            IngredientAndCount input = new IngredientAndCount(Ingredient.of(e.item()), e.count());
            trades.put(ShippingBinBridge.tradeId(id), new Trade(input, new ItemStack(e.coin(), e.coinCount()), Optional.empty()));
            newDetails.put(id, new Detail(e.value(), e.source()));
        }
        try {
            TradeManager manager = ModClient.getTradeManager();
            manager.replaceTrades(trades);
            ShippingBinBridge.updateInputs(manager, trades.values());
            // On a server, the bin must read this copy. Shipping Bin otherwise keeps using the list of the last
            // single player world played in this session, because it never forgets it.
            if (Minecraft.getInstance().getSingleplayerServer() == null) ShippingBin.setTradeManager(null);
        } catch (RuntimeException | LinkageError ex) {
            MintMachineEconomy.LOG.warn(ShippingValues.LOG + "Could not put the server's prices into the Shipping Bin: {}", ex.toString());
        }
        details = newDetails;
        received = true;
        MintMachineEconomy.LOG.info(ShippingValues.LOG + "Received {} Shipping Bin prices from the server", trades.size());
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        received = false;
        details = Map.of();
        pending.clear();
        pendingBatch = -1;
    }
}
