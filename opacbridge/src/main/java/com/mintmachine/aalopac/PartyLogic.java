package com.mintmachine.aalopac;

import java.util.Optional;
import java.util.UUID;

/**
 * The team rules Automated Logistics asks FTB Teams for, answered from Open Parties and Claims parties instead.
 * Pure logic over a {@link Parties} lookup so it can be tested without a game.
 *
 * Mapping from FTB Teams to OPAC:
 *  - "same team"        = both players are members of the same OPAC party
 *  - "allied"           = the owner's party lists the player's party as an ally (FTB: the owner's team gives the
 *                         player ally rank), and the player is not in the owner's party
 *  - team id            = the player's OPAC party id
 *  - vehicle-limit bucket = the owner's party id, or the owner if they have no party
 */
public final class PartyLogic {
    private PartyLogic() {}

    /** Server-side view of OPAC parties. */
    public interface Parties {
        /** The id of the party the player belongs to, if any. */
        Optional<UUID> partyOf(UUID player);
        /** Whether {@code party} lists {@code otherParty} as an ally. */
        boolean listsAsAlly(UUID party, UUID otherParty);
    }

    public static boolean areSameTeam(Parties parties, UUID first, UUID second) {
        if (first.equals(second)) return true;
        Optional<UUID> a = parties.partyOf(first);
        return a.isPresent() && a.equals(parties.partyOf(second));
    }

    public static boolean areAllied(Parties parties, UUID assetOwner, UUID player) {
        Optional<UUID> ownerParty = parties.partyOf(assetOwner);
        Optional<UUID> playerParty = parties.partyOf(player);
        if (ownerParty.isEmpty() || playerParty.isEmpty()) return false;
        if (ownerParty.get().equals(playerParty.get())) return false;   // members are teammates, not allies
        return parties.listsAsAlly(ownerParty.get(), playerParty.get());
    }

    public static Optional<UUID> teamIdForPlayer(Parties parties, UUID player) {
        return parties.partyOf(player);
    }

    public static UUID activeVehicleLimitBucket(Parties parties, UUID owner) {
        return parties.partyOf(owner).orElse(owner);
    }

    /** Exact answer, for when the server's party data is reachable (dedicated server, or singleplayer). */
    public static Optional<Boolean> canControl(Parties parties, UUID player, UUID owner, boolean allowTeam, boolean allowAllies) {
        if (allowTeam && areSameTeam(parties, player, owner)) return Optional.of(true);
        if (!allowAllies) return Optional.of(false);
        return Optional.of(areAllied(parties, owner, player));
    }

    /** Client-side view: only the local player's own party is known. */
    public interface OwnParty {
        /** True while OPAC is still syncing party data to the client. */
        boolean loading();
        /** The local player's party id, or empty if they have none. */
        Optional<UUID> id();
        boolean hasMember(UUID player);
        /** Whether the local player's party lists {@code party} as an ally. */
        boolean listsAsAlly(UUID party);
    }

    /**
     * Best answer a multiplayer client can give. Returns empty ("unknown", as FTB Teams does when its client data
     * is missing) when the owner's party can't be known; the server still makes the real decision.
     * For allies the client only knows which parties its own party lists, so it assumes the alliance is mutual.
     */
    public static Optional<Boolean> canControlFromClient(OwnParty mine, UUID owner, Optional<UUID> ownerPartyHint,
                                                         boolean allowTeam, boolean allowAllies) {
        if (mine.loading()) return Optional.empty();
        Optional<UUID> myParty = mine.id();
        if (myParty.isEmpty()) return Optional.of(false);
        boolean teammate = mine.hasMember(owner) || ownerPartyHint.map(myParty.get()::equals).orElse(false);
        if (teammate) return Optional.of(allowTeam);
        if (!allowAllies) return Optional.of(false);
        if (ownerPartyHint.isPresent()) return Optional.of(mine.listsAsAlly(ownerPartyHint.get()));
        return Optional.empty();
    }
}
