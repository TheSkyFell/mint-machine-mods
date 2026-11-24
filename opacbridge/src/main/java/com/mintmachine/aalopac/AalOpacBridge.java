package com.mintmachine.aalopac;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(AalOpacBridge.MOD_ID)
public final class AalOpacBridge {
    public static final String MOD_ID = "aal_opac_bridge";
    private static final Logger LOG = LogUtils.getLogger();

    public AalOpacBridge() {
        LOG.info("[aal_opac_bridge] Automated Logistics team checks {}", Bridge.active()
                ? "use Open Parties and Claims parties"
                : "use FTB Teams (installed), bridge idle");
    }
}
