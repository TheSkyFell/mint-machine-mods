package com.mintmachine.economy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * One part of the server's Shipping Bin prices, sent to a player by {@link PriceSync}. The full list (tens of thousands
 * of items) goes in parts of up to {@link #PART_SIZE} prices; every part of one sending has the same batch number.
 */
public record PricesPayload(int batch, int part, int parts, List<Entry> entries) implements CustomPacketPayload {
    static final int PART_SIZE = 2000;

    public static final Type<PricesPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MintMachineEconomy.MOD_ID, "shipping_prices"));

    /** One price: count items sell for coinCount of coin. value (in spurs) and source are the details the tooltips show. */
    public record Entry(Item item, int count, Item coin, int coinCount, double value, String source) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);

    public static final StreamCodec<RegistryFriendlyByteBuf, PricesPayload> CODEC = StreamCodec.of(PricesPayload::write, PricesPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, PricesPayload p) {
        buf.writeVarInt(p.batch);
        buf.writeVarInt(p.part);
        buf.writeVarInt(p.parts);
        buf.writeVarInt(p.entries.size());
        for (Entry e : p.entries) {
            ITEM.encode(buf, e.item);
            buf.writeVarInt(e.count);
            ITEM.encode(buf, e.coin);
            buf.writeVarInt(e.coinCount);
            buf.writeDouble(e.value);
            buf.writeUtf(e.source, 1024);
        }
    }

    private static PricesPayload read(RegistryFriendlyByteBuf buf) {
        int batch = buf.readVarInt();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        int size = buf.readVarInt();
        if (size < 0 || size > PART_SIZE) throw new IllegalArgumentException("Too many prices in one part: " + size);
        List<Entry> entries = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            entries.add(new Entry(ITEM.decode(buf), buf.readVarInt(), ITEM.decode(buf), buf.readVarInt(), buf.readDouble(), buf.readUtf(1024)));
        }
        return new PricesPayload(batch, part, parts, entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
