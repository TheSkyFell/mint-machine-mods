package xaero.pac.common.server.api;

import net.minecraft.server.MinecraftServer;
import xaero.pac.common.server.parties.party.api.IPartyManagerAPI;

/** Stub of Open Parties and Claims 0.33.0 (1.21.1). */
public class OpenPACServerAPI {
    private OpenPACServerAPI() {}

    public static OpenPACServerAPI get(MinecraftServer server) {
        throw new AssertionError("stub");
    }

    public IPartyManagerAPI getPartyManager() {
        throw new AssertionError("stub");
    }
}
