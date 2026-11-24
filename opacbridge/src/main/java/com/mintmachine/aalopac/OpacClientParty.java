package com.mintmachine.aalopac;

import xaero.pac.client.api.OpenPACClientAPI;
import xaero.pac.client.parties.party.api.IClientPartyAPI;
import xaero.pac.client.parties.party.api.IClientPartyStorageAPI;

import java.util.Optional;
import java.util.UUID;

/** The local player's own party, as OPAC syncs it to the client. Only loaded on the physical client. */
final class OpacClientParty implements PartyLogic.OwnParty {
    private final IClientPartyStorageAPI storage;

    private OpacClientParty(IClientPartyStorageAPI storage) {
        this.storage = storage;
    }

    static Optional<PartyLogic.OwnParty> get() {
        OpenPACClientAPI api = OpenPACClientAPI.get();
        if (api == null || api.getClientPartyStorage() == null) return Optional.empty();
        return Optional.of(new OpacClientParty(api.getClientPartyStorage()));
    }

    @Override
    public boolean loading() {
        return storage.isLoading();
    }

    @Override
    public Optional<UUID> id() {
        IClientPartyAPI party = storage.getParty();
        return party == null ? Optional.empty() : Optional.of(party.getId());
    }

    @Override
    public boolean hasMember(UUID player) {
        IClientPartyAPI party = storage.getParty();
        return party != null && party.getMemberInfo(player) != null;
    }

    @Override
    public boolean listsAsAlly(UUID otherParty) {
        IClientPartyAPI party = storage.getParty();
        return party != null && party.isAlly(otherParty);
    }
}
