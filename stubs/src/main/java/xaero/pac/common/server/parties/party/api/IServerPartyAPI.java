package xaero.pac.common.server.parties.party.api;

import java.util.UUID;

/** Stub of Open Parties and Claims 0.33.0 (1.21.1). The real interface extends IPartyAPI and redeclares these. */
public interface IServerPartyAPI {
    UUID getId();

    boolean isAlly(UUID partyId);
}
