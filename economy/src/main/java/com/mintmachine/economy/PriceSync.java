package com.mintmachine.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Sends the server's Shipping Bin prices to each player when they join, and to everyone online when the prices change.
 * <p>
 * Shipping Bin never sends its trade list to players, so on a dedicated server the player's own copy (which decides the
 * price tooltips and which items the bin accepts) would otherwise come from the price file in the player's own config
 * folder. That file can differ from the server's, for example when the server owner changes the economy settings.
 * The player's game puts the received prices into Shipping Bin's copy ({@link ClientPrices}).
 * <p>
 * Optional on both sides: a player without this mod version, or a server without it, still connects.
 */
public final class PriceSync {
    private PriceSync() {}

    private static List<PricesPayload> parts;
    private static int batch;

    static void register(IEventBus modBus, IEventBus game) {
        modBus.addListener(PriceSync::onRegisterPayloads);
        game.addListener(PriceSync::onPlayerLoggedIn);
        game.addListener(PriceSync::onServerStopped);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(PricesPayload.TYPE, PricesPayload.CODEC,
                (payload, context) -> context.enqueueWork(() -> ClientPrices.receive(payload)));
    }

    /** The prices the Shipping Bin now uses: item id -> [count, coin, coinCount, value, source]. */
    static void setPrices(MinecraftServer server, JsonObject trades) {
        parts = split(trades);
        int sent = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (send(player)) sent++;
        }
        if (sent > 0) MintMachineEconomy.LOG.info(ShippingValues.LOG + "Sent the new prices to {} players", sent);
    }

    private static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (parts == null) {
            // Prices weren't worked out this session (they failed, or the world is still loading): use the saved file
            JsonObject file = PriceCache.read();
            if (file == null || !file.has("trades")) return;
            parts = split(file.getAsJsonObject("trades"));
        }
        send(player);
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        parts = null;
    }

    private static boolean send(ServerPlayer player) {
        List<PricesPayload> p = parts;
        if (p == null || p.isEmpty() || !player.connection.hasChannel(PricesPayload.TYPE)) return false;
        PacketDistributor.sendToPlayer(player, p.get(0), p.subList(1, p.size()).toArray(new PricesPayload[0]));
        return true;
    }

    private static List<PricesPayload> split(JsonObject trades) {
        List<PricesPayload.Entry> all = new ArrayList<>(trades.size());
        for (Map.Entry<String, JsonElement> e : trades.entrySet()) {
            try {
                ResourceLocation id = ResourceLocation.tryParse(e.getKey());
                if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) continue;
                JsonArray t = e.getValue().getAsJsonArray();
                ResourceLocation coinId = ResourceLocation.tryParse(t.get(1).getAsString());
                if (coinId == null || !BuiltInRegistries.ITEM.containsKey(coinId)) continue;
                Item item = BuiltInRegistries.ITEM.get(id);
                Item coin = BuiltInRegistries.ITEM.get(coinId);
                double value = t.size() > 3 ? t.get(3).getAsDouble() : Double.NaN;
                String source = t.size() > 4 ? t.get(4).getAsString() : "";
                if (source.length() > 1000) source = source.substring(0, 1000);
                all.add(new PricesPayload.Entry(item, t.get(0).getAsInt(), coin, t.get(2).getAsInt(), value, source));
            } catch (RuntimeException ex) {
                // a malformed entry: leave it out
            }
        }
        int b = ++batch;
        int count = Math.max(1, (all.size() + PricesPayload.PART_SIZE - 1) / PricesPayload.PART_SIZE);
        List<PricesPayload> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int from = i * PricesPayload.PART_SIZE;
            int to = Math.min(all.size(), from + PricesPayload.PART_SIZE);
            out.add(new PricesPayload(b, i, count, List.copyOf(all.subList(from, to))));
        }
        return out;
    }
}
