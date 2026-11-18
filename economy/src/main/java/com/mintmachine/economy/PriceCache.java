package com.mintmachine.economy;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * config/mintmachine_economy/shipping_prices.json: the worked-out prices,
 * {hash, items, fingerprint, trades: {item id: [count, coin, coin count, value, where the value came from]}}.
 * Same layout as the KubeJS script's kubejs/config/shipping_values.json.
 */
final class PriceCache {
    private PriceCache() {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    static Path file() {
        return EconomySettings.folder().resolve("shipping_prices.json");
    }

    /** The saved prices, or null if there are none or they can't be read. */
    static JsonObject read() {
        Path path = file();
        if (!Files.exists(path)) return null;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (Exception e) {
            MintMachineEconomy.LOG.warn(ShippingValues.LOG + "Could not read {}: {}", path, e.toString());
            return null;
        }
    }

    static void write(JsonObject json) {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(json, writer);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            MintMachineEconomy.LOG.error(ShippingValues.LOG + "Could not save prices to {}: {}", path, e.toString());
        }
    }
}
