package dev.ithundxr.createnumismatics.content.depositor;

import dev.ithundxr.createnumismatics.content.backend.Coin;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Stub of Create: Numismatics 1.1.0. The real class extends AbstractDepositorBlockEntity. */
public abstract class BrassDepositorBlockEntity extends BlockEntity {
    private BrassDepositorBlockEntity() {
        super(null, null, null);
    }

    public int getPrice(Coin coin) {
        throw new AssertionError("stub");
    }
}
