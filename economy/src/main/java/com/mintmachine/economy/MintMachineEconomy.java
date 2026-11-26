package com.mintmachine.economy;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Create: Mint &amp; Machine - Economy. Four parts, each a port of one of the modpack's KubeJS scripts:
 * <ul>
 * <li>{@link VillagerCoins}: villagers and wandering traders trade in Numismatics coins.</li>
 * <li>{@link ShippingValues} and {@link ShippingPack}: a Shipping Bin sell price for every item, sent to players by
 * {@link PriceSync}.</li>
 * <li>{@link CoinChange}: change for coins at villagers and shops, and bank cards at villagers.</li>
 * <li>{@link FishBonus}: golden and big Starcatcher fish sell for more.</li>
 * </ul>
 * Settings are in config/mintmachine_economy/settings.json.
 */
@Mod(MintMachineEconomy.MOD_ID)
public final class MintMachineEconomy {
    public static final String MOD_ID = "mintmachine_economy";
    static final Logger LOG = LogUtils.getLogger();

    public MintMachineEconomy(IEventBus modBus) {
        EconomySettings.reload();
        modBus.addListener(ShippingPack::onAddPackFinders);

        IEventBus game = NeoForge.EVENT_BUS;
        game.addListener(EconomySettings::onServerAboutToStart);
        game.addListener(ShippingValues::onServerStarted);
        PriceSync.register(modBus, game);
        if (FMLEnvironment.dist.isClient()) ClientPrices.register(game);
        VillagerCoins.register(game);
        CoinChange.register(game);
        if (ModList.get().isLoaded("starcatcher")) FishBonus.register(game);
    }
}
