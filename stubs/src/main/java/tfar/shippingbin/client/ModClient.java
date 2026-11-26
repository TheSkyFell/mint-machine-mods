package tfar.shippingbin.client;

import tfar.shippingbin.trades.TradeManager;

/**
 * Stub of Shipping Bin 1 (1.21.1). The player's own copy of the trade list: Shipping Bin fills it only from a packet it
 * never sends, and creates it on first use from the client level's registries.
 */
public class ModClient {
    public static TradeManager getTradeManager() {
        throw new AssertionError("stub");
    }
}
