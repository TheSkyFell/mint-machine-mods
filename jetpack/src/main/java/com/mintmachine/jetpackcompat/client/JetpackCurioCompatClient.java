package com.mintmachine.jetpackcompat.client;

import static com.mintmachine.jetpackcompat.JetpackItems.*;

import com.mintmachine.jetpackcompat.JetpackCurioCompat;
import com.mintmachine.jetpackcompat.NjFlight;
import com.mintmachine.jetpackcompat.NjFlyPayload;

import de.fabenet.createnj.init.CreateNjModKeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client half: lets the Netherite Jetpack fly from the Curios Body slot. create_nj's fly key (Space by default)
 * only switches on a jetpack in the chest armour slot. While a body-slot Netherite Jetpack is worn (and the chest
 * slot holds no jetpack), this reads the same key, tells the server when it changes, sets the jetpack's "flight" flag
 * on this client's copy so thrust starts without waiting for the server, and runs create_nj's jetpack tick every
 * client tick (movement happens on the player's own client).
 */
@Mod(value = JetpackCurioCompat.MOD_ID, dist = Dist.CLIENT)
public final class JetpackCurioCompatClient {
    private static boolean lastSent;

    public JetpackCurioCompatClient() {
        if (ModList.get().isLoaded("create_nj")) NeoForge.EVENT_BUS.addListener(JetpackCurioCompatClient::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.getConnection() == null) {
            lastSent = false;
            return;
        }
        if (mc.isPaused()) return;

        String chestId = idOf(player.getItemBySlot(EquipmentSlot.CHEST));
        ItemStack jetpack = isChestJetpack(chestId) ? null : bodyNjJetpack(player);
        KeyMapping fly = CreateNjModKeyMappings.FLY;
        boolean down = jetpack != null && fly != null && fly.isDown();
        if (down != lastSent) {
            PacketDistributor.sendToServer(new NjFlyPayload(down));
            lastSent = down;
        }
        if (jetpack == null) return;
        NjFlight.tick(player, jetpack, down);
    }

    private static ItemStack bodyNjJetpack(LocalPlayer player) {
        for (ItemStack s : curioStacks(player, BODY_SLOT)) {
            if (NJ_JETPACK.equals(idOf(s))) return s;
        }
        return null;
    }
}
