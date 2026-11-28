package com.mintmachine.aerofixes;

import com.simibubi.create.content.contraptions.AssemblyException;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper.AssemblyTransform;
import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.simulated_team.simulated.util.SimAssemblyHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Taking a ship apart overwrote whatever was in the way (aeronautics_review item 4): Sable puts each block in its new
 * place whatever is there, so chests, machines, an End portal or another player's wall were simply replaced. The
 * Physics Assembler only checked height, speed and nearness to the ground.
 * <p>
 * This works out where every block of the ship will land, exactly as the assembler will (the same Sable transform,
 * anchored on the assembler, turned by its disassembly angle), and refuses with the assembler's usual error display if
 * any landing spot holds a block that has a block entity or a collision shape and can't be replaced. Water, grass,
 * snow, flowers and torches are still replaced as before. Used by
 * {@link com.mintmachine.aerofixes.mixin.compat.PhysicsAssemblerDisassemblyMixin}.
 */
public final class DisassemblyCheck {
    private DisassemblyCheck() {}

    public static void check(BlockEntity assembler, ServerSubLevel ship, int disassemblyAngle) throws AssemblyException {
        if (!AeroFixesConfig.disassemblyCheck() || !(assembler.getLevel() instanceof ServerLevel level)) return;

        BlockPos anchor = assembler.getBlockPos();
        BlockPos goal = BlockPos.containing(Sable.HELPER.projectOutOfSubLevel(level, Vec3.atCenterOf(anchor)));
        Rotation rotation = SimAssemblyHelper.rotationFrom90DegRots(disassemblyAngle);
        AssemblyTransform transform = new AssemblyTransform(anchor, goal,
                rotation == Rotation.NONE ? 0 : 4 - rotation.ordinal(), rotation, level);

        BoundingBox3ic box = ship.getPlot().getBoundingBox();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int y = box.minY(); y <= box.maxY(); y++) {
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).isAir()) continue;
                    BlockPos target = transform.apply(pos.immutable());
                    BlockState there = level.getBlockState(target);
                    if (inTheWay(level, target, there)) {
                        throw new AssemblyException(Component.translatableWithFallback(
                                "message." + AeroFixes.MOD_ID + ".disassembly_blocked",
                                "Can't take the ship apart here: %s at %s, %s, %s is in the way",
                                there.getBlock().getName(), target.getX(), target.getY(), target.getZ()));
                    }
                }
            }
        }
    }

    private static boolean inTheWay(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir() || state.canBeReplaced()) return false;
        return state.hasBlockEntity() || !state.getCollisionShape(level, pos).isEmpty();
    }
}
