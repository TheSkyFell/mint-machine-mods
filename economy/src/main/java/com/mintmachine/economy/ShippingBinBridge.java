package com.mintmachine.economy;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import tfar.shippingbin.IngredientAndCount;
import tfar.shippingbin.ShippingBin;
import tfar.shippingbin.trades.Trade;
import tfar.shippingbin.trades.TradeManager;

/** Puts prices straight into the Shipping Bin's in-memory trade list. */
final class ShippingBinBridge {
    private ShippingBinBridge() {}

    /** The id a price's trade has: shipping_values:namespace/path. Other code relies on this form. */
    static ResourceLocation tradeId(String itemId) {
        int i = itemId.indexOf(':');
        return ResourceLocation.fromNamespaceAndPath("shipping_values", itemId.substring(0, i) + "/" + itemId.substring(i + 1));
    }

    record Result(int applied, boolean inputsUpdated) {}

    /** From a JsonObject of item id -> [count, coin, coinCount, ...]. */
    static Result apply(JsonObject trades) {
        Map<ResourceLocation, Trade> live = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : trades.entrySet()) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getKey());
            if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) continue; // item from a removed mod
            JsonArray t = e.getValue().getAsJsonArray();
            Item item = BuiltInRegistries.ITEM.get(rl);
            Item coin = BuiltInRegistries.ITEM.get(ResourceLocation.parse(t.get(1).getAsString()));
            IngredientAndCount input = new IngredientAndCount(Ingredient.of(item), t.get(0).getAsInt());
            live.put(tradeId(e.getKey()), new Trade(input, new ItemStack(coin, t.get(2).getAsInt()), Optional.empty()));
        }
        TradeManager manager = ShippingBin.getTradeManager();
        manager.replaceTrades(live);
        return new Result(live.size(), updateInputs(manager, live.values()));
    }

    private static Field allowedInputs;

    /**
     * The bin only accepts items in its allowedInputs list, which it otherwise builds only while loading data packs.
     * Rebuilt here to match the new trades, so new items can go in without a reload. False if that isn't possible.
     */
    @SuppressWarnings("unchecked")
    private static boolean updateInputs(TradeManager manager, Collection<Trade> trades) {
        try {
            if (allowedInputs == null) {
                Field f = TradeManager.class.getDeclaredField("allowedInputs");
                f.setAccessible(true);
                allowedInputs = f;
            }
            Set<Ingredient> set = (Set<Ingredient>) allowedInputs.get(manager);
            set.clear();
            for (Trade t : trades) set.add(t.input().ingredient());
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            MintMachineEconomy.LOG.warn(ShippingValues.LOG + "Could not update the Shipping Bin's accepted items directly: {}", e.toString());
            return false;
        }
    }
}
