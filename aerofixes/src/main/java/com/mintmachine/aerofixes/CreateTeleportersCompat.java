package com.mintmachine.aerofixes;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Create Teleporters Remastered 3.0.2 and ships. Its entity teleporter already teleports to a position on a loaded ship
 * correctly ({@code SableAeronauticsIntegration.teleportEntity} moves plot-grid targets onto the ship), but two steps
 * before that didn't know about ships:
 * <ol>
 * <li>The Quantum Linker finds the block it's aimed at with its own block-by-block search of the world's chunks, which
 * looks straight through a ship and saves whatever is behind it. {@link #shipAwareLinkTarget} also asks the level
 * (whose ray cast Sable extends to ships) and takes the ship's block when it's nearer.</li>
 * <li>The teleporter's range check (500 blocks by default) adds up the raw coordinate differences. A teleporter or a
 * target on a ship has plot-grid coordinates about 20 million blocks out, so any link between a ship and anywhere
 * else was "Out Of Range". {@link #shipAwareRange} hands the check a range that makes it compare world distances.</li>
 * <li>Its teleport then left the player at the ship's stored position (an unpaired Sable push/pop), which crashed the
 * server. {@link #entityLocal} skips that pair.</li>
 * </ol>
 * Used by the mixins in {@code com.mintmachine.aerofixes.mixin.compat}.
 */
public final class CreateTeleportersCompat {
    private CreateTeleportersCompat() {}

    /**
     * @param found what the linker's own search found (a {@link BlockHitResult})
     * @return the block on a ship the linker is aimed at if that's nearer than what it found, otherwise {@code found}
     */
    public static Object shipAwareLinkTarget(Entity entity, Vec3 from, Vec3 to, Object found) {
        if (!AeroFixesConfig.createTeleporters() || !(found instanceof BlockHitResult worldHit)) return found;
        Level level = entity.level();
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.BLOCK || !inPlotGrid(level, hit.getBlockPos().getX(), hit.getBlockPos().getZ())) {
            return found;
        }
        double toShip = from.distanceToSqr(Sable.HELPER.projectOutOfSubLevel(level, hit.getLocation()));
        double toWorld = worldHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : from.distanceToSqr(worldHit.getLocation());
        return toShip <= toWorld ? hit : found;
    }

    /**
     * The teleporter at x, y, z goes "Out Of Range" when |x - tx| + |y - ty| + |z - tz| reaches the range, with tx, ty,
     * tz from the linker in its slot. When either end is on a ship, this returns the range shifted by the difference
     * between that raw distance and the same distance between the two ends' world positions, so the check compares
     * world distances. A ship that isn't loaded has no world position, so it stays out of range.
     *
     * @param range the configured range (a Double), as the teleporter read it
     */
    public static Object shipAwareRange(Object range, LevelAccessor accessor, double x, double y, double z, ItemStack linker) {
        if (!AeroFixesConfig.createTeleporters() || !(accessor instanceof Level level) || !(range instanceof Number r)) {
            return range;
        }
        CompoundTag tag = linker.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        double tx = tag.getDouble("xpo"), ty = tag.getDouble("ypo"), tz = tag.getDouble("zpo");
        if (!inPlotGrid(level, x, z) && !inPlotGrid(level, tx, tz)) return range;

        double raw = Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz);
        Vec3 a = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(x, y, z));
        Vec3 b = Sable.HELPER.projectOutOfSubLevel(level, new Vec3(tx, ty, tz));
        double world = Math.abs(a.x - b.x) + Math.abs(a.y - b.y) + Math.abs(a.z - b.z);
        return Double.valueOf(r.doubleValue() + (raw - world));
    }

    /**
     * Stands in for Create Teleporters' own pop/push of an entity into a ship's stored coordinates around its teleport.
     * With the fix on it does nothing; switched off, it calls the original by reflection.
     */
    public static void entityLocal(String method, Object subLevel, Entity entity) {
        if (AeroFixesConfig.createTeleporters()) return;
        try {
            var m = Class.forName("net.createteleporters.integration.SableAeronauticsIntegration")
                    .getDeclaredMethod(method, Object.class, Entity.class);
            m.setAccessible(true);
            m.invoke(null, subLevel, entity);
        } catch (ReflectiveOperationException e) {
            AeroFixes.LOG.warn("[{}] Couldn't call Create Teleporters' {}", AeroFixes.MOD_ID, method, e);
        }
    }

    private static boolean inPlotGrid(Level level, double x, double z) {
        return Sable.HELPER.isInPlotGrid(level, Mth.floor(x) >> 4, Mth.floor(z) >> 4);
    }
}
