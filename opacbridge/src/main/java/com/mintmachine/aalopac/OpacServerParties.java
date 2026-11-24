package com.mintmachine.aalopac;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import xaero.pac.common.server.api.OpenPACServerAPI;
import xaero.pac.common.server.parties.party.api.IServerPartyAPI;

import java.util.Optional;
import java.util.UUID;

/** Reads parties from OPAC's server API. Only usable while a server (dedicated or integrated) is running. */
public final class OpacServerParties implements PartyLogic.Parties {
    private final OpenPACServerAPI api;

    private OpacServerParties(OpenPACServerAPI api) {
        this.api = api;
    }

    /** The current server's parties, or empty when no server is running in this JVM (a multiplayer client). */
    public static Optional<OpacServerParties> current() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return Optional.empty();
        try {
            OpenPACServerAPI api = OpenPACServerAPI.get(server);
            return api == null ? Optional.empty() : Optional.of(new OpacServerParties(api));
        } catch (RuntimeException notReady) {
            // OPAC's server data isn't loaded yet (very early in server start)
            return Optional.empty();
        }
    }

    @Override
    public Optional<UUID> partyOf(UUID player) {
        IServerPartyAPI party = api.getPartyManager().getPartyByMember(player);
        return party == null ? Optional.empty() : Optional.of(party.getId());
    }

    @Override
    public boolean listsAsAlly(UUID party, UUID otherParty) {
        IServerPartyAPI p = api.getPartyManager().getPartyById(party);
        return p != null && p.isAlly(otherParty);
    }
}
