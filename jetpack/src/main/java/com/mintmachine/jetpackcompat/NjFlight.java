package com.mintmachine.jetpackcompat;

import de.fabenet.createnj.procedures.JetpackTickEventProcedure;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Runs create_nj's own jetpack tick for a Netherite Jetpack worn in the Curios Body slot (create_nj only ticks its
 * jetpack in the chest armour slot). On the server it uses fuel and resets fall damage; on the player's own client
 * it moves the player. Only touched when create_nj is installed.
 */
public final class NjFlight {
    private NjFlight() {}

    private static boolean failed;

    public static void tick(Player player, ItemStack jetpack, boolean flying) {
        if (failed) return;
        if (JetpackItems.getFlag(jetpack, "flight") != flying) JetpackItems.setFlag(jetpack, "flight", flying);
        try {
            JetpackTickEventProcedure.execute(player.level(), player.getX(), player.getY(), player.getZ(), player, jetpack);
        } catch (LinkageError e) {
            failed = true;
            JetpackCurioCompat.LOG.error("[{}] create_nj's JetpackTickEventProcedure isn't usable, so the Netherite Jetpack "
                    + "won't fly from the Curios Body slot: {}", JetpackCurioCompat.MOD_ID, e.toString());
        }
    }
}
