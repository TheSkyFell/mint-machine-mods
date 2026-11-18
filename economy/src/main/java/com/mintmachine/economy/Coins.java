package com.mintmachine.economy;

import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** The Numismatics coin ladder. */
public final class Coins {
    private Coins() {}

    /** One coin type. */
    public record Coin(String id, int value, String singular, String plural) {
        public Item item() {
            return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
        }
    }

    /** Largest coin first. */
    public static final List<Coin> LARGEST_FIRST = List.of(
            new Coin("numismatics:sun", 4096, "Sun", "Suns"),
            new Coin("numismatics:crown", 512, "Crown", "Crowns"),
            new Coin("numismatics:cog", 64, "Cog", "Cogs"),
            new Coin("numismatics:sprocket", 16, "Sprocket", "Sprockets"),
            new Coin("numismatics:bevel", 8, "Bevel", "Bevels"),
            new Coin("numismatics:spur", 1, "Spur", "Spurs"));

    /** Spurs per coin for a coin id, or 0 if it isn't a coin. */
    public static int valueOf(String id) {
        for (Coin c : LARGEST_FIRST) if (c.id.equals(id)) return c.value;
        return 0;
    }

    public static Coin byId(String id) {
        for (Coin c : LARGEST_FIRST) if (c.id.equals(id)) return c;
        return null;
    }

    /** Whether every coin item is registered. */
    public static boolean present() {
        for (Coin c : LARGEST_FIRST) {
            if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(c.id))) return false;
        }
        return true;
    }

    public static String idOf(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String idOf(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : idOf(stack.getItem());
    }

    static boolean isAir(Item item) {
        return item == Items.AIR;
    }
}
