package com.mintmachine.economy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;

/**
 * A built-in data pack, kept in memory, that turns the saved prices into Shipping Bin trades on every data load
 * (world load and /reload): data/shipping_values/trades/namespace/path.json for each priced item, and an empty file
 * over each of the Shipping Bin's own emerald trades (default_trades), which switches them off. It's always enabled
 * and sits at the top of the data pack list. With no saved prices yet it's empty; the prices are worked out once the
 * world has loaded and put into the bin directly.
 */
final class ShippingPack implements PackResources {
    static final String ID = MintMachineEconomy.MOD_ID + ":shipping_prices";
    private static final String TRADES = "trades/";

    static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        PackLocationInfo info = new PackLocationInfo(ID, Component.literal("Mint & Machine Shipping Bin prices"),
                PackSource.BUILT_IN, Optional.empty());
        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackResources openPrimary(PackLocationInfo location) {
                return new ShippingPack(location);
            }

            @Override
            public PackResources openFull(PackLocationInfo location, Pack.Metadata metadata) {
                return new ShippingPack(location);
            }
        };
        Pack pack = Pack.readMetaAndCreate(info, supplier, PackType.SERVER_DATA,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) event.addRepositorySource(consumer -> consumer.accept(pack));
        else MintMachineEconomy.LOG.error("[{}] Could not create the Shipping Bin prices data pack", MintMachineEconomy.MOD_ID);
    }

    private final PackLocationInfo location;
    private Map<ResourceLocation, byte[]> files;

    private ShippingPack(PackLocationInfo location) {
        this.location = location;
    }

    /** Built when first needed, from the saved prices as they are now. */
    private synchronized Map<ResourceLocation, byte[]> files() {
        if (files != null) return files;
        Map<ResourceLocation, byte[]> out = new LinkedHashMap<>();
        JsonObject json = PriceCache.read();
        if (json == null || !json.has("trades") || !json.get("trades").isJsonObject()) {
            MintMachineEconomy.LOG.info(ShippingValues.LOG + "No saved prices yet; they will be worked out when the world finishes loading.");
            files = out;
            return out;
        }
        byte[] empty = "{}".getBytes(StandardCharsets.UTF_8);
        for (String name : EconomySettings.get().defaultTrades) {
            ResourceLocation rl = ResourceLocation.tryBuild("shippingbin", TRADES + name + ".json");
            if (rl != null) out.put(rl, empty);
        }
        int added = 0, skipped = 0;
        for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("trades").entrySet()) {
            String id = e.getKey();
            ResourceLocation itemId = ResourceLocation.tryParse(id);
            if (itemId == null || !BuiltInRegistries.ITEM.containsKey(itemId)) { // item from a removed mod
                skipped++;
                continue;
            }
            try {
                JsonArray t = e.getValue().getAsJsonArray();
                JsonObject trade = new JsonObject();
                JsonObject input = new JsonObject();
                input.addProperty("count", t.get(0).getAsInt());
                JsonObject ingredient = new JsonObject();
                ingredient.addProperty("item", id);
                input.add("ingredient", ingredient);
                trade.add("input", input);
                JsonObject output = new JsonObject();
                output.addProperty("id", t.get(1).getAsString());
                output.addProperty("count", t.get(2).getAsInt());
                trade.add("output", output);
                ResourceLocation tradeId = ShippingBinBridge.tradeId(id);
                out.put(tradeId.withPrefix(TRADES).withSuffix(".json"), trade.toString().getBytes(StandardCharsets.UTF_8));
                added++;
            } catch (Exception ex) {
                skipped++;
            }
        }
        MintMachineEconomy.LOG.info(ShippingValues.LOG + "Added {} Shipping Bin trades{}.", added,
                skipped > 0 ? " (skipped " + skipped + " for items that no longer exist)" : "");
        files = out;
        return out;
    }

    private static byte[] packMeta() {
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
        return ("{\"pack\":{\"description\":\"Mint & Machine Shipping Bin prices\",\"pack_format\":" + format + "}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getRootResource(String... elements) {
        if (elements.length == 1 && elements[0].equals(PACK_META)) {
            byte[] meta = packMeta();
            return () -> new ByteArrayInputStream(meta);
        }
        return null;
    }

    @Nullable
    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != PackType.SERVER_DATA) return null;
        byte[] data = files().get(location);
        return data == null ? null : () -> new ByteArrayInputStream(data);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type != PackType.SERVER_DATA) return;
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<ResourceLocation, byte[]> e : files().entrySet()) {
            ResourceLocation rl = e.getKey();
            if (rl.getNamespace().equals(namespace) && rl.getPath().startsWith(prefix)) {
                byte[] data = e.getValue();
                output.accept(rl, () -> new ByteArrayInputStream(data));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of("shippingbin", "shipping_values") : Set.of();
    }

    @Nullable
    @Override
    public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) throws IOException {
        try (InputStream in = new ByteArrayInputStream(packMeta())) {
            return AbstractPackResources.getMetadataFromStream(deserializer, in);
        }
    }

    @Override
    public PackLocationInfo location() {
        return location;
    }

    @Override
    public void close() {
    }
}
