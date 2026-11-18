package com.mintmachine.economy;

import java.util.List;

import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.stockTicker.StockTickerInteractionHandler;
import com.simibubi.create.content.logistics.tableCloth.ShoppingListItem;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Create shopkeepers: exact change right before paying with a Shopping List. Only touched when Create is installed. */
final class CreateShops {
    private CreateShops() {}

    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (!EconomySettings.get().shops || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND || !CoinChange.ready()) return;
        try {
            ItemStack list = player.getMainHandItem();
            if (list.isEmpty() || !Coins.idOf(list.getItem()).equals("create:shopping_list")) return;
            if (CoinChange.isAnyCard(player.getOffhandItem())) return; // the card pays
            if (StockTickerInteractionHandler.getStockTickerPosition(event.getTarget()) == null) return;
            ShoppingListItem.ShoppingList shopping = ShoppingListItem.getList(list);
            if (shopping == null) return;
            List<BigItemStack> stacks = shopping.bakeEntries(event.getLevel(), null).getSecond().getStacksByCount();
            int[] price = CoinChange.zero();
            for (BigItemStack big : stacks) {
                int ci = CoinChange.coinOf(big.stack);
                if (ci >= 0) price[ci] += big.count;
            }
            CoinChange.payExact(player, price, 1);
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(CoinChange.LOG + "Shopkeeper change failed: {}", e.toString());
        }
    }
}
