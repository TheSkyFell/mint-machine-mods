package velrondevs.botania.common.block.flower.functional;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Stub of Botania Revived 2.0.2. The real hierarchy is FunctionalFlowerBlockEntity (redstoneSignal, getMana,
 * addMana) &lt; BindableSpecialFlowerBlockEntity &lt; SpecialFlowerBlockEntity (getEffectivePos) &lt; BlockEntity.
 */
public abstract class AgricarnationBlockEntity extends BlockEntity {
    public int redstoneSignal;

    private AgricarnationBlockEntity() {
        super(null, null, null);
    }

    public int getRange() {
        throw new AssertionError("stub");
    }

    public int getMana() {
        throw new AssertionError("stub");
    }

    public void addMana(int mana) {
        throw new AssertionError("stub");
    }

    public final BlockPos getEffectivePos() {
        throw new AssertionError("stub");
    }
}
