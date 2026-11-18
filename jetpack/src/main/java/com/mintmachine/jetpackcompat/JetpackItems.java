package com.mintmachine.jetpackcompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/** Item ids and small helpers shared by the server and client halves. */
public final class JetpackItems {
    private JetpackItems() {}

    public static final Set<String> FUELING_TANKS = Set.of(
            "create_sa:small_fueling_tank", "create_sa:medium_fueling_tank", "create_sa:large_fueling_tank");
    public static final Set<String> FILLING_TANKS = Set.of(
            "create_sa:small_filling_tank", "create_sa:medium_filling_tank", "create_sa:large_filling_tank");
    public static final String CREATIVE_TANK = "create_sa:creative_filling_tank";
    /** The jetpacks Curios Jetpacks flies from the body slot (its JETPACKS map). */
    public static final Set<String> SA_JETPACKS = Set.of(
            "create_sa:copper_jetpack_chestplate", "create_sa:andesite_jetpack_chestplate",
            "create_sa:brass_jetpack_chestplate", "create_sa:netherite_jetpack_chestplate");
    public static final String NJ_JETPACK = "create_nj:netherite_jetpack_chestplate";
    public static final String NJ_EXOSKELETON = "create_nj:netherite_exoskeleton_chestplate";

    public static final String TANK_SLOT = "tank";
    public static final String BELT_SLOT = "belt";
    public static final String BODY_SLOT = "body";

    public static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key.toString();
    }

    public static boolean isTank(String id) {
        return FUELING_TANKS.contains(id) || FILLING_TANKS.contains(id) || CREATIVE_TANK.equals(id);
    }

    public static boolean isChestJetpack(String id) {
        return NJ_JETPACK.equals(id) || SA_JETPACKS.contains(id);
    }

    /** The non-empty stacks in one of the player's Curios slot types (empty if the slot type doesn't exist). */
    public static List<ItemStack> curioStacks(Player player, String slotId) {
        List<ItemStack> out = new ArrayList<>();
        CuriosApi.getCuriosInventory(player)
                .flatMap(inv -> inv.getStacksHandler(slotId))
                .ifPresent(handler -> {
                    IDynamicStackHandler stacks = handler.getStacks();
                    for (int i = 0; i < stacks.getSlots(); i++) {
                        ItemStack s = stacks.getStackInSlot(i);
                        if (!s.isEmpty()) out.add(s);
                    }
                });
        return out;
    }

    public static boolean getFlag(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(key);
    }

    public static void setFlag(ItemStack stack, String key, boolean value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(key, value));
    }

    public static double getNumber(ItemStack stack, String key) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble(key);
    }

    public static void setNumber(ItemStack stack, String key, double value) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble(key, value));
    }
}
