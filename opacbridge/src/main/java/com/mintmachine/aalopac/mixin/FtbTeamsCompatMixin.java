package com.mintmachine.aalopac.mixin;

import com.mintmachine.aalopac.Bridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/**
 * Automated Logistics 0.7.0 routes every team question through the static methods of FtbTeamsCompat, which
 * answer "no team" when FTB Teams isn't installed. Each injection answers from OPAC parties instead.
 * Targets are Automated Logistics' own (unobfuscated) class, so no refmap or remapping is involved.
 */
@Mixin(targets = "net.sprocketgames.create_aeronautics_automated_logistics.compat.FtbTeamsCompat", remap = false)
public abstract class FtbTeamsCompatMixin {

    @Inject(method = "areSameTeam(Ljava/util/UUID;Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$areSameTeam(UUID first, UUID second, CallbackInfoReturnable<Boolean> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.areSameTeam(first, second));
    }

    @Inject(method = "areAllied(Ljava/util/UUID;Ljava/util/UUID;)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$areAllied(UUID assetOwner, UUID player, CallbackInfoReturnable<Boolean> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.areAllied(assetOwner, player));
    }

    @Inject(method = "activeVehicleLimitBucket(Ljava/util/UUID;)Ljava/util/UUID;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$bucket(UUID owner, CallbackInfoReturnable<UUID> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.activeVehicleLimitBucket(owner));
    }

    @Inject(method = "teamIdForPlayer(Ljava/util/UUID;)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$teamId(UUID player, CallbackInfoReturnable<Optional<UUID>> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.teamIdForPlayer(player));
    }

    @Inject(method = "canControlClientSide(Ljava/util/UUID;Ljava/util/UUID;ZZ)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$canControl(UUID player, UUID owner, boolean allowTeam, boolean allowAllies,
                                           CallbackInfoReturnable<Optional<Boolean>> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.canControlClientSide(player, owner, Optional.empty(), allowTeam, allowAllies));
    }

    @Inject(method = "canControlClientSide(Ljava/util/UUID;Ljava/util/UUID;Ljava/util/Optional;ZZ)Ljava/util/Optional;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void aalopac$canControlWithHint(UUID player, UUID owner, Optional<UUID> ownerTeamHint, boolean allowTeam,
                                                   boolean allowAllies, CallbackInfoReturnable<Optional<Boolean>> cir) {
        if (Bridge.active()) cir.setReturnValue(Bridge.canControlClientSide(player, owner, ownerTeamHint, allowTeam, allowAllies));
    }
}
