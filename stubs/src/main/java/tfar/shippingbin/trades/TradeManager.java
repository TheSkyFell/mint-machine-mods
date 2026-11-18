package tfar.shippingbin.trades;

import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Stub of Shipping Bin 1 (1.21.1). The real class extends SimpleJsonResourceReloadListener and has a package-private
 * {@code Set<Ingredient> allowedInputs}, reached by reflection.
 */
public abstract class TradeManager {
    private TradeManager() {}

    public Map<ResourceLocation, Trade> getTrades() {
        throw new AssertionError("stub");
    }

    public void replaceTrades(Map<ResourceLocation, Trade> trades) {
        throw new AssertionError("stub");
    }

    public boolean isInput(ItemStack stack) {
        throw new AssertionError("stub");
    }
}
