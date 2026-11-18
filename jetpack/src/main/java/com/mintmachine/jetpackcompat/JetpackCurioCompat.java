package com.mintmachine.jetpackcompat;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Fills the gaps between Create SA Curios Jetpacks, Create Stuff 'N Additions (create_sa) and Create Stuff &amp;
 * Netherite Additions (create_nj):
 * <ol>
 * <li>A Curios "tank" slot (2 slots, for Stuff 'N Additions' tanks) that refuels a jetpack worn in the Curios Body
 * slot, at the same rate Curios Jetpacks uses for inventory and belt tanks: 10 mB per tank per tick.</li>
 * <li>The Netherite Jetpack and Netherite Exoskeleton refuel from Stuff 'N Additions' tanks in the inventory, the
 * Belt slot or the Tank slot. create_nj reads the tanks' old "tagStock" number, which create_sa 2.1.4b no longer
 * uses, so without this they never refuel.</li>
 * <li>The Netherite Jetpack flies from the Curios Body slot.</li>
 * </ol>
 */
@Mod(JetpackCurioCompat.MOD_ID)
public final class JetpackCurioCompat {
    public static final String MOD_ID = "sa_jetpack_curio_compat";
    static final Logger LOG = LogUtils.getLogger();

    /** Players holding create_nj's fly key while wearing a body-slot Netherite Jetpack (server side). */
    private static final Set<UUID> FLYING = ConcurrentHashMap.newKeySet();

    static boolean createNjLoaded;

    public JetpackCurioCompat(IEventBus modBus) {
        createNjLoaded = ModList.get().isLoaded("create_nj");
        modBus.addListener(JetpackCurioCompat::registerPayloads);
        NeoForge.EVENT_BUS.addListener(JetpackCurioCompat::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(JetpackCurioCompat::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(JetpackCurioCompat::onLoggedOut);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(NjFlyPayload.TYPE, NjFlyPayload.STREAM_CODEC, (payload, context) -> {
            UUID id = context.player().getUUID();
            if (payload.down()) FLYING.add(id);
            else FLYING.remove(id);
        });
    }

    static boolean isFlying(ServerPlayer player) {
        return FLYING.contains(player.getUUID());
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) JetpackRefuel.tick(player);
    }

    // A key held at logout must not leave the jetpack thrusting on the next login
    private static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        FLYING.remove(event.getEntity().getUUID());
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FLYING.remove(event.getEntity().getUUID());
    }
}
