package com.mintmachine.jetpackcompat;

import static com.mintmachine.jetpackcompat.JetpackItems.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;

/** Server side, every player tick: refuelling from tanks, and body-slot Netherite Jetpack flight. */
final class JetpackRefuel {
    private JetpackRefuel() {}

    static final int NJ_MAX = 6400;          // create_nj's tagFuel/tagWater capacity
    static final double MB_PER_UNIT = 10;    // 1 old tagStock point
    static final int STEP_MB = 10;           // per tank per tick, as in Curios Jetpacks
    static final int CREATIVE_NJ_UNITS = 64; // like create_nj's own creative tank code
    static final int CREATIVE_SA_MB = 500;   // like Curios Jetpacks
    static final int MAIN_INVENTORY = 36;

    static void tick(ServerPlayer player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        String chestId = idOf(chest);
        ItemStack bodyNj = null;
        List<ItemStack> bodySa = new ArrayList<>();
        for (ItemStack s : curioStacks(player, BODY_SLOT)) {
            String id = idOf(s);
            if (NJ_JETPACK.equals(id) && bodyNj == null) bodyNj = s;
            else if (SA_JETPACKS.contains(id)) bodySa.add(s);
        }
        boolean chestNj = NJ_JETPACK.equals(chestId) || NJ_EXOSKELETON.equals(chestId);
        if (!chestNj && bodyNj == null && bodySa.isEmpty()) return;

        List<ItemStack> tankSlot = tanksIn(curioStacks(player, TANK_SLOT));
        if (chestNj || bodyNj != null) {
            List<ItemStack> njTanks = new ArrayList<>();
            for (int i = 0; i < MAIN_INVENTORY; i++) {
                ItemStack s = player.getInventory().getItem(i);
                if (isTank(idOf(s))) njTanks.add(s);
            }
            njTanks.addAll(tanksIn(curioStacks(player, BELT_SLOT)));
            njTanks.addAll(tankSlot);
            if (chestNj) refuelNj(chest, njTanks);
            if (bodyNj != null) refuelNj(bodyNj, njTanks);
        }
        for (ItemStack jetpack : bodySa) refuelSaFromTankSlot(jetpack, tankSlot);

        if (bodyNj != null && !isChestJetpack(chestId) && JetpackCurioCompat.createNjLoaded) {
            NjFlight.tick(player, bodyNj, JetpackCurioCompat.isFlying(player));
        }
    }

    private static List<ItemStack> tanksIn(List<ItemStack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack s : stacks) if (isTank(idOf(s))) out.add(s);
        return out;
    }

    private static IFluidHandlerItem fluidHandler(ItemStack stack) {
        return stack.getCapability(Capabilities.FluidHandler.ITEM);
    }

    /** Up to {@code mb} of the tank's own fluid, if that fluid is {@code fluid}; else null. */
    private static FluidStack offer(IFluidHandler handler, Fluid fluid, int mb) {
        if (handler.getTanks() < 1) return null;
        FluidStack inTank = handler.getFluidInTank(0);
        if (inTank.isEmpty() || !inTank.getFluid().isSame(fluid)) return null;
        return inTank.copyWithAmount(Math.min(mb, inTank.getAmount()));
    }

    /** Drains up to {@code mb} of {@code fluid} from a tank; returns the amount drained. */
    private static int drainTank(ItemStack tank, Fluid fluid, int mb) {
        IFluidHandlerItem h = fluidHandler(tank);
        if (h == null) return 0;
        FluidStack req = offer(h, fluid, mb);
        if (req == null) return 0;
        FluidStack got = h.drain(req, IFluidHandler.FluidAction.EXECUTE);
        return got.isEmpty() ? 0 : got.getAmount();
    }

    // --- Netherite Jetpack / Exoskeleton: tank fluid -> tagFuel / tagWater ----------------------------------

    /** Like create_nj's own refuel: past 6399 counts as full. */
    private static double njClamp(double value) {
        return value > NJ_MAX - 1 ? NJ_MAX : value;
    }

    private static void refuelNj(ItemStack item, List<ItemStack> tanks) {
        for (ItemStack tank : tanks) {
            String id = idOf(tank);
            if (CREATIVE_TANK.equals(id)) {
                for (String key : new String[]{"tagFuel", "tagWater"}) {
                    double have = getNumber(item, key);
                    if (have < NJ_MAX - 1) setNumber(item, key, njClamp(have + CREATIVE_NJ_UNITS));
                }
                continue;
            }
            boolean fueling = FUELING_TANKS.contains(id);
            if (!fueling && !FILLING_TANKS.contains(id)) continue;
            String key = fueling ? "tagFuel" : "tagWater";
            double cur = getNumber(item, key);
            if (cur >= NJ_MAX - 1) continue; // full, as create_nj counts it
            int want = (int) Math.min(STEP_MB, Math.ceil((NJ_MAX - cur) * MB_PER_UNIT));
            int got = drainTank(tank, fueling ? Fluids.LAVA : Fluids.WATER, want);
            if (got > 0) setNumber(item, key, njClamp(cur + got / MB_PER_UNIT));
        }
    }

    // --- Tank slot -> body-slot Stuff 'N Additions jetpack (fluid handler to fluid handler) -------------------
    // The jetpack's own handler refuses a fluid it can't take, so no tag check is needed

    private static void moveFluid(ItemStack tank, ItemStack jetpack, Fluid fluid, int mb) {
        IFluidHandlerItem from = fluidHandler(tank), to = fluidHandler(jetpack);
        if (from == null || to == null) return;
        FluidStack req = offer(from, fluid, mb);
        if (req == null) return;
        int fits = to.fill(req, IFluidHandler.FluidAction.SIMULATE);
        if (fits <= 0) return;
        FluidStack drained = from.drain(req.copyWithAmount(Math.min(fits, req.getAmount())), IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) to.fill(drained, IFluidHandler.FluidAction.EXECUTE);
    }

    private static void refuelSaFromTankSlot(ItemStack jetpack, List<ItemStack> tanks) {
        for (ItemStack tank : tanks) {
            String id = idOf(tank);
            if (CREATIVE_TANK.equals(id)) {
                IFluidHandlerItem to = fluidHandler(jetpack);
                if (to == null) continue;
                to.fill(new FluidStack(Fluids.LAVA, CREATIVE_SA_MB), IFluidHandler.FluidAction.EXECUTE);
                to.fill(new FluidStack(Fluids.WATER, CREATIVE_SA_MB), IFluidHandler.FluidAction.EXECUTE);
                continue;
            }
            if (FUELING_TANKS.contains(id)) moveFluid(tank, jetpack, Fluids.LAVA, STEP_MB);
            if (FILLING_TANKS.contains(id)) moveFluid(tank, jetpack, Fluids.WATER, STEP_MB);
        }
    }
}
