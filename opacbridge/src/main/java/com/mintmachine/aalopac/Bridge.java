package com.mintmachine.aalopac;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.Optional;
import java.util.UUID;

/** Entry points called from the mixin into Automated Logistics' FtbTeamsCompat. */
public final class Bridge {
    private Bridge() {}

    /** The bridge only answers when FTB Teams is absent; with FTB Teams installed, Automated Logistics uses it. */
    public static boolean active() {
        return !ModList.get().isLoaded("ftbteams") && ModList.get().isLoaded("openpartiesandclaims");
    }

    public static boolean areSameTeam(UUID first, UUID second) {
        return OpacServerParties.current().map(p -> PartyLogic.areSameTeam(p, first, second)).orElse(first.equals(second));
    }

    public static boolean areAllied(UUID assetOwner, UUID player) {
        return OpacServerParties.current().map(p -> PartyLogic.areAllied(p, assetOwner, player)).orElse(false);
    }

    public static Optional<UUID> teamIdForPlayer(UUID player) {
        return OpacServerParties.current().flatMap(p -> PartyLogic.teamIdForPlayer(p, player));
    }

    public static UUID activeVehicleLimitBucket(UUID owner) {
        return OpacServerParties.current().map(p -> PartyLogic.activeVehicleLimitBucket(p, owner)).orElse(owner);
    }

    /**
     * Automated Logistics calls this only from client screens, to decide what to show; the server makes the real
     * permission check through areSameTeam/areAllied. The client's own party data answers first. If that can't
     * decide and a server runs in this JVM (singleplayer or LAN host), the exact server answer is used.
     */
    public static Optional<Boolean> canControlClientSide(UUID player, UUID owner, Optional<UUID> ownerTeamHint,
                                                         boolean allowTeam, boolean allowAllies) {
        Optional<Boolean> fromClient = Optional.empty();
        if (FMLEnvironment.dist == Dist.CLIENT) {
            fromClient = OpacClientParty.get()
                    .flatMap(mine -> PartyLogic.canControlFromClient(mine, owner, ownerTeamHint, allowTeam, allowAllies));
        }
        if (fromClient.isPresent()) return fromClient;
        try {
            return OpacServerParties.current().flatMap(p -> PartyLogic.canControl(p, player, owner, allowTeam, allowAllies));
        } catch (RuntimeException raceWithServerThread) {
            return Optional.empty();
        }
    }
}
