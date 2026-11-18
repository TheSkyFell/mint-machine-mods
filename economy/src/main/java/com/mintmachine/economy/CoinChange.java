package com.mintmachine.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.ithundxr.createnumismatics.Numismatics;
import dev.ithundxr.createnumismatics.content.backend.BankAccount;
import dev.ithundxr.createnumismatics.content.backend.Coin;
import dev.ithundxr.createnumismatics.content.backend.ReasonHolder;
import dev.ithundxr.createnumismatics.content.bank.CardItem;
import dev.ithundxr.createnumismatics.content.depositor.BrassDepositorBlockEntity;
import dev.ithundxr.createnumismatics.content.vendor.VendorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Change for Numismatics coins, and bank cards at villagers (port of coin_change.js).
 * <p>
 * Villagers (and wandering traders, Easy Villagers trader blocks) want exact coins: a trade that costs 3 cogs can't
 * be paid with a crown. While a trade screen is open, this keeps enough small coins in the inventory to pay for the
 * trades the player can afford, by breaking bigger coins down the coin ladder. When the screen closes, loose coins
 * are merged back up the ladder.
 * <p>
 * Bank card at villagers: a bound Numismatics Bank Card in the offhand when the trade screen opens. If the pocket
 * can't cover a trade, the coins come out of the account; when the screen closes, the pocket goes back to what it
 * held when it opened, and the account pays for what was bought and takes in the coins from what was sold.
 * <p>
 * Shops: Numismatics Vendors, Brass Depositors and Create shopkeepers also want exact coins. Right before they take
 * payment, coins are broken down to the exact amount, and the change is merged back a tick later.
 * <p>
 * Money is never created or lost: every step is one coin exchange, or a bank withdrawal or deposit of exactly the
 * value of the coins added or removed. Only the main inventory and hotbar are touched.
 */
final class CoinChange {
    private CoinChange() {}

    static final String LOG = "[coin_change] ";

    // Smallest coin first
    static final String[] IDS = {"numismatics:spur", "numismatics:bevel", "numismatics:sprocket", "numismatics:cog",
            "numismatics:crown", "numismatics:sun"};
    static final String[] NAMES = {"spur", "bevel", "sprocket", "cog", "crown", "sun"};
    static final int[] VALUE = {1, 8, 16, 64, 512, 4096};
    static final int N = 6;

    private static Item[] items;
    private static int[] maxs;

    static void register(IEventBus bus) {
        bus.addListener(CoinChange::onPlayerTick);
        bus.addListener(CoinChange::onLoggedOut);
        bus.addListener(EventPriority.HIGHEST, CoinChange::onRightClickBlock);
        if (ModList.get().isLoaded("create")) bus.addListener(EventPriority.HIGHEST, CreateShops::onEntityInteractSpecific);
    }

    private static void log(String msg) {
        if (EconomySettings.get().changeDebug) MintMachineEconomy.LOG.info(LOG + msg);
    }

    /** Looks the coins up once; false if Numismatics' coins aren't there. */
    static boolean ready() {
        if (items != null) return true;
        for (String id : IDS) if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id))) return false;
        Item[] it = new Item[N];
        int[] mx = new int[N];
        for (int i = 0; i < N; i++) {
            it[i] = BuiltInRegistries.ITEM.get(ResourceLocation.parse(IDS[i]));
            mx[i] = new ItemStack(it[i]).getMaxStackSize();
        }
        maxs = mx;
        items = it;
        return true;
    }

    static int[] zero() {
        return new int[N];
    }

    static long valueOf(int[] c) {
        long v = 0;
        for (int i = 0; i < N; i++) v += (long) c[i] * VALUE[i];
        return v;
    }

    static String fmt(long spurs) {
        return spurs + "¤";
    }

    static void tell(Player player, String text) {
        player.displayClientMessage(Component.literal(text).withStyle(ChatFormatting.GOLD), true);
    }

    /** Coin index of a stack, or -1. */
    static int coinOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        Item item = stack.getItem();
        for (int i = 0; i < N; i++) if (items[i] == item) return i;
        return -1;
    }

    // ---------------------------------------------------------------- the pocket (main inventory + hotbar)
    static final int MAIN = 36;
    /** Where new stacks go: the main inventory first, the hotbar last. */
    static final int[] FILL_ORDER = new int[MAIN];

    static {
        int k = 0;
        for (int s = 9; s < MAIN; s++) FILL_ORDER[k++] = s;
        for (int s = 0; s < 9; s++) FILL_ORDER[k++] = s;
    }

    static int[] countCoins(Inventory inv) {
        int[] c = zero();
        for (int s = 0; s < MAIN; s++) {
            ItemStack st = inv.getItem(s);
            int i = coinOf(st);
            if (i >= 0) c[i] += st.getCount();
        }
        return c;
    }

    /** How many more coins of type i fit. */
    static int room(Inventory inv, int i) {
        int r = 0;
        for (int s = 0; s < MAIN; s++) {
            ItemStack st = inv.getItem(s);
            if (st.isEmpty()) r += maxs[i];
            else if (coinOf(st) == i) r += Math.max(0, maxs[i] - st.getCount());
        }
        return r;
    }

    /** Adds n coins of type i; returns how many didn't fit. */
    static int add(Inventory inv, int i, int n) {
        for (int s = 0; s < MAIN && n > 0; s++) {
            ItemStack st = inv.getItem(s);
            if (coinOf(st) == i && st.getCount() < maxs[i]) {
                int k = Math.min(n, maxs[i] - st.getCount());
                st.grow(k);
                n -= k;
            }
        }
        for (int f = 0; f < FILL_ORDER.length && n > 0; f++) {
            int slot = FILL_ORDER[f];
            if (inv.getItem(slot).isEmpty()) {
                int k = Math.min(n, maxs[i]);
                inv.setItem(slot, new ItemStack(items[i], k));
                n -= k;
            }
        }
        return n;
    }

    /** Removes up to n coins of type i, last slots first; returns how many were removed. */
    static int remove(Inventory inv, int i, int n) {
        int done = 0;
        for (int s = MAIN - 1; s >= 0 && done < n; s--) {
            ItemStack st = inv.getItem(s);
            if (coinOf(st) == i) {
                int k = Math.min(n - done, st.getCount());
                st.shrink(k);
                done += k;
                if (st.isEmpty()) inv.setItem(s, ItemStack.EMPTY);
            }
        }
        return done;
    }

    static void dropCoins(Player player, int i, int n) {
        while (n > 0) {
            int k = Math.min(n, maxs[i]);
            player.drop(new ItemStack(items[i], k), false);
            n -= k;
        }
    }

    /** Breaks one coin of type j into the next coin down (one coin exchange). False if there's no room. */
    static boolean breakOne(Inventory inv, int[] c, int j) {
        if (j < 1 || c[j] < 1) return false;
        int ratio = VALUE[j] / VALUE[j - 1];
        if (room(inv, j - 1) < ratio) return false;
        remove(inv, j, 1);
        int left = add(inv, j - 1, ratio);
        if (left > 0) { // can't happen after the room check; put things back as they were
            remove(inv, j - 1, ratio - left);
            add(inv, j, 1);
            return false;
        }
        c[j] -= 1;
        c[j - 1] += ratio;
        return true;
    }

    record Ensure(boolean done, boolean broke, boolean noRoom) {}

    /**
     * Makes sure the pocket holds at least want[i] coins of each type. Breaks bigger coins first, smallest needs
     * first, so making small change never uses up coins a bigger need is counting on. If that isn't enough (the trade
     * wants a bigger coin than any in the pocket), the pocket is rebuilt as exactly what's wanted plus the rest in the
     * fewest coins, which merges small coins up the ladder.
     */
    static Ensure ensure(Player player, int[] want) {
        Inventory inv = player.getInventory();
        int[] c = countCoins(inv);
        boolean done = true, broke = false, noRoom = false;
        for (int i = 0; i < N && done; i++) {
            while (c[i] < want[i]) {
                int j = i + 1;
                while (j < N && c[j] == 0) j++;
                if (j >= N) {
                    done = false;
                    break;
                }
                boolean ok = true;
                for (int k = j; k > i && ok; k--) ok = breakOne(inv, c, k);
                if (!ok) {
                    done = false;
                    noRoom = true;
                    break;
                }
                broke = true;
            }
        }
        if (!done && !noRoom && valueOf(c) >= valueOf(want)) {
            int[] t = canonical(valueOf(c) - valueOf(want));
            for (int i = 0; i < N; i++) t[i] += want[i];
            if (setTo(player, t, false)) {
                done = true;
                broke = true;
            } else noRoom = true;
        }
        if (broke) inv.setChanged();
        return new Ensure(done, broke, noRoom);
    }

    /** The fewest coins worth spurs. */
    static int[] canonical(long spurs) {
        int[] c = zero();
        for (int i = N - 1; i >= 0; i--) {
            c[i] = (int) Math.min(Integer.MAX_VALUE, spurs / VALUE[i]);
            spurs -= (long) c[i] * VALUE[i];
        }
        return c;
    }

    /** Would changing the pocket to the counts t fit? Runs remove() and add() on a copy of the slots. */
    static boolean fits(Inventory inv, int[] t) {
        int[] slotCoin = new int[MAIN]; // -1 empty, -2 something else, else coin index
        int[] slotCount = new int[MAIN];
        int[] c = zero();
        for (int s = 0; s < MAIN; s++) {
            ItemStack st = inv.getItem(s);
            int i = st.isEmpty() ? -1 : coinOf(st);
            slotCoin[s] = st.isEmpty() ? -1 : (i >= 0 ? i : -2);
            slotCount[s] = st.getCount();
            if (i >= 0) c[i] += st.getCount();
        }
        for (int i = 0; i < N; i++) {
            int n = c[i] - t[i];
            for (int s = MAIN - 1; s >= 0 && n > 0; s--) {
                if (slotCoin[s] != i) continue;
                int k = Math.min(n, slotCount[s]);
                slotCount[s] -= k;
                n -= k;
                if (slotCount[s] == 0) slotCoin[s] = -1;
            }
        }
        for (int i = 0; i < N; i++) {
            int n = t[i] - c[i];
            for (int s = 0; s < MAIN && n > 0; s++) {
                if (slotCoin[s] == i && slotCount[s] < maxs[i]) {
                    int k = Math.min(n, maxs[i] - slotCount[s]);
                    slotCount[s] += k;
                    n -= k;
                }
            }
            for (int f = 0; f < FILL_ORDER.length && n > 0; f++) {
                int s = FILL_ORDER[f];
                if (slotCoin[s] == -1) {
                    int k = Math.min(n, maxs[i]);
                    slotCoin[s] = i;
                    slotCount[s] = k;
                    n -= k;
                }
            }
            if (n > 0) return false;
        }
        return true;
    }

    /**
     * Changes the pocket to hold exactly t[i] coins of each type, moving as few coins as it can. The value changes by
     * the difference: callers pair that with a bank deposit or withdrawal, or keep the value the same. With allowDrop,
     * coins that don't fit are dropped at the player's feet (never lost); without it, nothing changes unless
     * everything fits.
     */
    static boolean setTo(Player player, int[] t, boolean allowDrop) {
        Inventory inv = player.getInventory();
        if (!allowDrop && !fits(inv, t)) return false;
        int[] c = countCoins(inv);
        for (int i = 0; i < N; i++) if (c[i] > t[i]) remove(inv, i, c[i] - t[i]);
        for (int i = 0; i < N; i++) {
            if (t[i] > c[i]) {
                int left = add(inv, i, t[i] - c[i]);
                if (left > 0) dropCoins(player, i, left);
            }
        }
        inv.setChanged();
        return true;
    }

    /** The pocket in the fewest coins worth target. */
    static void recompose(Player player, long target) {
        setTo(player, canonical(Math.max(0, target)), true);
    }

    static void tidy(Player player) {
        recompose(player, valueOf(countCoins(player.getInventory())));
    }

    // ---------------------------------------------------------------- bank cards

    static boolean isAnyCard(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = Coins.idOf(stack.getItem());
        return id.startsWith("numismatics:") && id.endsWith("_card") && !id.endsWith("_id_card");
    }

    /** The bank account of a bound Bank Card in the offhand, if this player may use it. */
    private static BankAccount cardAccount(Player player) {
        if (!EconomySettings.get().villagerCard) return null;
        try {
            ItemStack card = player.getOffhandItem();
            if (card.isEmpty() || !(card.getItem() instanceof CardItem) || !CardItem.isBound(card)) return null;
            UUID id = CardItem.get(card);
            BankAccount account = id == null ? null : Numismatics.BANK.getAccount(id);
            if (account == null || !account.isAuthorized(player)) return null;
            return account;
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(LOG + "Could not read the bank card: {}", e.toString());
            return null;
        }
    }

    private static boolean withdraw(BankAccount account, long spurs) {
        if (spurs <= 0 || account.getMaxWithdrawal() < spurs) return false;
        if (!account.deduct((int) spurs, new ReasonHolder())) return false;
        account.markDirty();
        return true;
    }

    private static void deposit(BankAccount account, long spurs) {
        if (spurs <= 0) return;
        account.deposit((int) spurs);
        account.markDirty();
    }

    // ---------------------------------------------------------------- villager trade screens

    private static final class Session {
        final int id;
        final long start;
        final BankAccount account;
        long withdrawn;
        boolean broke;
        boolean toldRoom;
        boolean toldBank;

        Session(int id, long start, BankAccount account) {
            this.id = id;
            this.start = start;
            this.account = account;
        }
    }

    private static final Map<UUID, Session> sessions = new HashMap<>();
    private static final Set<UUID> pendingTidy = new HashSet<>();

    private static Session startSession(ServerPlayer player, MerchantMenu menu) {
        Session s = new Session(menu.containerId, valueOf(countCoins(player.getInventory())), cardAccount(player));
        sessions.put(player.getUUID(), s);
        log(player.getGameProfile().getName() + " opened a trade screen with " + fmt(s.start) + (s.account != null ? ", bank card in offhand" : ""));
        return s;
    }

    private record Need(int[] r, long value) {}

    /** Keeps enough change in the pocket to pay for the trades the player can afford. */
    private static void topUp(ServerPlayer player, MerchantMenu menu, Session s) {
        Inventory inv = player.getInventory();
        int[] pocket = countCoins(inv);
        int[] inSlots = zero();
        for (int k = 0; k < 2; k++) {
            ItemStack st = menu.getSlot(k).getItem();
            int ci = coinOf(st);
            if (ci >= 0) inSlots[ci] += st.getCount();
        }
        long budget = valueOf(pocket) + valueOf(inSlots) + (s.account != null ? Math.max(0, s.account.getMaxWithdrawal()) : 0);

        // Each trade's price in coins (with the villager's discounts and demand)
        List<Need> trades = new ArrayList<>();
        MerchantOffers offers = menu.getOffers();
        for (MerchantOffer offer : offers) {
            if (offer.isOutOfStock()) continue;
            int[] r = zero();
            boolean paid = false;
            for (ItemStack cost : new ItemStack[]{offer.getCostA(), offer.getCostB()}) {
                int cq = coinOf(cost);
                if (cq >= 0) {
                    r[cq] += cost.getCount();
                    paid = true;
                }
            }
            if (paid) trades.add(new Need(r, valueOf(r)));
        }
        if (trades.isEmpty()) return;

        // Change for as many trades as the budget covers, cheapest first
        trades.sort((a, b) -> Long.compare(a.value, b.value));
        int[] need = zero();
        for (Need t : trades) {
            int[] next = need.clone();
            for (int i = 0; i < N; i++) next[i] = Math.max(next[i], t.r[i]);
            if (valueOf(next) <= budget) need = next;
        }
        // Coins already in the trade slots count: clicking a trade puts them back in the inventory first
        int[] want = zero();
        boolean lacking = false;
        for (int i = 0; i < N; i++) {
            want[i] = Math.max(0, need[i] - inSlots[i]);
            if (pocket[i] < want[i]) lacking = true;
        }
        if (!lacking) return;

        Ensure res = ensure(player, want);
        boolean noRoom = res.noRoom;
        if (res.broke) s.broke = true;
        if (!res.done && s.account != null) {
            // Still short: take the missing coins from the bank
            int[] c = countCoins(inv);
            for (int i = 0; i < N; i++) {
                int missing = want[i] - c[i];
                if (missing <= 0) continue;
                if (room(inv, i) < missing) {
                    noRoom = true;
                    continue;
                }
                if (withdraw(s.account, (long) missing * VALUE[i])) {
                    add(inv, i, missing);
                    s.withdrawn += (long) missing * VALUE[i];
                    s.broke = true;
                    log(player.getGameProfile().getName() + ": took " + missing + " " + NAMES[i] + " from the bank");
                    if (!s.toldBank) {
                        tell(player, "Paying from your bank account (" + fmt(s.account.getMaxWithdrawal()) + " left)");
                        s.toldBank = true;
                    }
                }
            }
            inv.setChanged();
        }
        if (noRoom && !s.toldRoom) {
            tell(player, "No room in your inventory for change");
            s.toldRoom = true;
        }
    }

    /** The trade screen closed: settle with the bank card, or tidy the change. */
    private static void finish(ServerPlayer player, Session s) {
        sessions.remove(player.getUUID());
        if (!player.isAlive()) return; // the inventory dropped on death; nothing to settle
        boolean tidy = EconomySettings.get().tidy;
        long now = valueOf(countCoins(player.getInventory()));
        if (s.account != null) {
            // The pocket goes back to what it held at the start; the account covers the difference
            long net = -s.withdrawn;
            if (now > s.start) {
                recompose(player, s.start);
                deposit(s.account, now - s.start);
                net += now - s.start;
            } else if (now < s.start) {
                long owed = Math.min(s.start - now, Math.max(0, s.account.getMaxWithdrawal()));
                if (owed > 0 && withdraw(s.account, owed)) {
                    recompose(player, now + owed);
                    net -= owed;
                } else if (tidy && s.broke) tidy(player);
            } else if (tidy && s.broke) tidy(player);
            if (net != 0) {
                String balance = fmt(s.account.getMaxWithdrawal());
                tell(player, net < 0 ? "Bank card: paid " + fmt(-net) + " (balance " + balance + ")"
                        : "Bank card: " + fmt(net) + " deposited (balance " + balance + ")");
            }
            log(player.getGameProfile().getName() + ": bank card visit, net " + fmt(net));
            return;
        }
        if (tidy && s.broke) tidy(player);
    }

    // ---------------------------------------------------------------- shops: exact change right before paying

    /** Breaks coins so the pocket holds price x times exactly (fewer times if that's all the player can afford). */
    static void payExact(ServerPlayer player, int[] price, int times) {
        long total = valueOf(price);
        if (total <= 0) return;
        long have = valueOf(countCoins(player.getInventory()));
        times = (int) Math.min(times, have / total);
        if (times < 1) return;
        int[] want = new int[N];
        for (int i = 0; i < N; i++) want[i] = price[i] * times;
        Ensure res = ensure(player, want);
        if (res.broke) {
            log(player.getGameProfile().getName() + ": made change for " + fmt(total) + " x" + times);
            if (EconomySettings.get().tidy) pendingTidy.add(player.getUUID());
        }
        if (res.noRoom) tell(player, "No room in your inventory for change");
    }

    @FunctionalInterface
    private interface PriceSource {
        int price(Coin coin);
    }

    private static int[] priceOf(PriceSource source) {
        int[] p = zero();
        for (Coin coin : Coin.values()) {
            int idx = -1;
            for (int i = 0; i < N; i++) if (VALUE[i] == coin.value) idx = i;
            if (idx >= 0) p[idx] += source.price(coin);
        }
        return p;
    }

    // Numismatics Vendors (selling) and Brass Depositors: before the block takes payment
    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!EconomySettings.get().shops || !(event.getEntity() instanceof ServerPlayer player)
                || event.getHand() != InteractionHand.MAIN_HAND || !ready()) return;
        try {
            if (isAnyCard(player.getMainHandItem())) return; // the card pays
            BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
            if (be == null) return;
            int times = 1;
            int[] price;
            if (be instanceof VendorBlockEntity vendor) {
                if (vendor.getMode() != VendorBlockEntity.Mode.SELL) return;
                if (player.isShiftKeyDown()) {
                    if (vendor.isTrustedInternal(player)) return; // the owner opening its settings
                    ItemStack stack = vendor.getStackToSell();
                    if (!stack.isEmpty()) times = Math.max(1, stack.getMaxStackSize() / Math.max(1, stack.getCount()));
                }
                price = priceOf(vendor::getPrice);
            } else if (be instanceof BrassDepositorBlockEntity depositor) {
                if (player.isShiftKeyDown()) return;
                price = priceOf(depositor::getPrice);
            } else return;
            payExact(player, price, times);
        } catch (Exception | LinkageError e) {
            MintMachineEconomy.LOG.error(LOG + "Vendor change failed: {}", e.toString());
        }
    }

    // ---------------------------------------------------------------- ticking

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ready()) return;
        try {
            UUID k = player.getUUID();
            Session s = sessions.get(k);
            boolean trading = player.containerMenu instanceof MerchantMenu;
            if (s != null && (!trading || player.containerMenu.containerId != s.id)) {
                finish(player, s);
                s = null;
            }
            if (trading) {
                MerchantMenu menu = (MerchantMenu) player.containerMenu;
                if (s == null) s = startSession(player, menu);
                if (player.tickCount % 2 == 0) topUp(player, menu, s);
            } else if (pendingTidy.remove(k)) {
                tidy(player);
            }
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(LOG + "Failed for {}: {}", player.getGameProfile().getName(), e.toString());
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ready()) return;
        try {
            UUID k = player.getUUID();
            Session s = sessions.get(k);
            if (s != null) {
                // Still trading: coins in the trade slots go back to the pocket first, so the visit settles correctly
                if (player.containerMenu instanceof MerchantMenu menu && menu.containerId == s.id) {
                    Inventory inv = player.getInventory();
                    for (int slot = 0; slot < 2; slot++) {
                        ItemStack st = menu.getSlot(slot).getItem();
                        int ci = coinOf(st);
                        if (ci < 0) continue;
                        int n = st.getCount();
                        menu.getSlot(slot).set(ItemStack.EMPTY);
                        int left = add(inv, ci, n);
                        if (left > 0) dropCoins(player, ci, left);
                    }
                }
                finish(player, s);
            }
            if (pendingTidy.remove(k)) tidy(player);
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(LOG + "Failed at logout for {}: {}", player.getGameProfile().getName(), e.toString());
        }
    }
}
