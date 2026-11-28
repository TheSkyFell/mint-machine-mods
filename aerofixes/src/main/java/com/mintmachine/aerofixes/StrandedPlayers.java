package com.mintmachine.aerofixes;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import dev.ryanhcode.sable.Sable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A player saved at a position inside Sable's plot grid (where ships keep their blocks, ~20 million blocks out) can't
 * be loaded safely: the server generates terrain around a joining player, and terrain generation out there crashes.
 * A bad teleport can leave a player there (seen 11-28 with Create Teleporters), and then every join crashes the server.
 * When such a player's data is read, before they're placed in the world, this moves them to the world spawn in the
 * Overworld. They're told once they've joined. Used by {@link com.mintmachine.aerofixes.mixin.ServerPlayerRescueMixin}.
 */
public final class StrandedPlayers {
    private StrandedPlayers() {}

    private static final Set<UUID> MOVED = ConcurrentHashMap.newKeySet();

    /** Called at the end of {@code ServerPlayer.readAdditionalSaveData}, with the tag the player was read from. */
    public static void rescueIfStranded(ServerPlayer player, CompoundTag tag) {
        if (!AeroFixesConfig.rescueStranded()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        ServerLevel saved = savedLevel(server, tag);
        if (saved == null || !Sable.HELPER.isInPlotGrid(saved, Mth.floor(player.getX()) >> 4, Mth.floor(player.getZ()) >> 4)) {
            return;
        }

        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        AeroFixes.LOG.warn("[{}] {} was saved at {}, {}, {} in {}, inside the ships' storage area; moving them to the world spawn {}",
                AeroFixes.MOD_ID, player.getGameProfile().getName(), player.getX(), player.getY(), player.getZ(),
                saved.dimension().location(), spawn);
        // PlayerList reads the dimension from this same tag after loading the player
        tag.putString("Dimension", Level.OVERWORLD.location().toString());
        player.setPos(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        MOVED.add(player.getUUID());
    }

    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (MOVED.remove(event.getEntity().getUUID())) {
            event.getEntity().displayClientMessage(Component.translatableWithFallback(
                    "message." + AeroFixes.MOD_ID + ".stranded_rescued",
                    "You were stuck in the ships' storage area, so you've been moved to the world spawn."), false);
        }
    }

    private static ServerLevel savedLevel(MinecraftServer server, CompoundTag tag) {
        ResourceLocation id = tag.contains("Dimension") ? ResourceLocation.tryParse(tag.getString("Dimension")) : null;
        if (id == null) return server.overworld();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level != null ? level : server.overworld();
    }
}
