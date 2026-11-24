package xaero.pac.client.parties.party.api;

import java.util.UUID;

import xaero.pac.common.parties.party.member.api.IPartyMemberAPI;

/** Stub of Open Parties and Claims 0.33.0 (1.21.1). The real interface extends IPartyAPI and redeclares these. */
public interface IClientPartyAPI {
    UUID getId();

    IPartyMemberAPI getMemberInfo(UUID memberId);

    boolean isAlly(UUID partyId);
}
