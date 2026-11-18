package dev.ithundxr.createnumismatics.content.vendor;

import dev.ithundxr.createnumismatics.content.backend.Coin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Stub of Create: Numismatics 1.1.0. The real class extends Create's SmartBlockEntity. */
public abstract class VendorBlockEntity extends BlockEntity {
    private VendorBlockEntity() {
        super(null, null, null);
    }

    public enum Mode {
        SELL, BUY
    }

    public boolean isTrustedInternal(Player player) {
        throw new AssertionError("stub");
    }

    public int getPrice(Coin coin) {
        throw new AssertionError("stub");
    }

    public ItemStack getStackToSell() {
        throw new AssertionError("stub");
    }

    public Mode getMode() {
        throw new AssertionError("stub");
    }
}
