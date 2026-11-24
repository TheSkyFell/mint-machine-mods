package xaero.pac.common.server.parties.party.api;

import java.util.UUID;

/** Stub of Open Parties and Claims 0.33.0 (1.21.1). */
public interface IPartyManagerAPI {
    IServerPartyAPI getPartyById(UUID id);

    IServerPartyAPI getPartyByMember(UUID memberId);
}
