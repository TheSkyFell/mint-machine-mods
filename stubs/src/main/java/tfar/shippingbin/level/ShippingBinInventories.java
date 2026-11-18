package tfar.shippingbin.level;

import java.util.Map;
import java.util.UUID;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.server.MinecraftServer;
import tfar.shippingbin.inventory.CommonHandler;

/** Stub of Shipping Bin 1 (1.21.1). The real class extends SavedData. */
public abstract class ShippingBinInventories {
    private ShippingBinInventories() {}

    public static ShippingBinInventories getOrCreateInstance(MinecraftServer server) {
        throw new AssertionError("stub");
    }

    public Map<UUID, Pair<CommonHandler, CommonHandler>> getHandlerMap() {
        throw new AssertionError("stub");
    }
}
