package com.mintmachine.combat;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ModifyDefaultComponentsEvent;

/**
 * Rebalances weapons and armor that outclass their place in the progression. The numbers live in
 * config/mintmachine_combat.json (see {@link CombatConfig} for the defaults and what each one means).
 *
 * Attack damage, armor, toughness and knockback resistance go through ItemAttributeModifierEvent: only the
 * item's own default modifiers (whatever ids its mod gave them) are replaced, so anything added on top by other
 * code is left alone, and tooltips and combat both see the new values. Durability goes through
 * ModifyDefaultComponentsEvent, which changes the item's default components once at startup.
 */
@Mod(CombatRebalance.MOD_ID)
public final class CombatRebalance {
    public static final String MOD_ID = "mintmachine_combat";
    static final Logger LOG = LogUtils.getLogger();

    private static CombatConfig config;
    /** Per-item settings, keyed by Item once registries are complete (built in common setup). */
    private static volatile Map<Item, CombatConfig.ItemSettings> byItem;

    public CombatRebalance(IEventBus modBus) {
        config = CombatConfig.load();
        modBus.addListener(CombatRebalance::onCommonSetup);
        modBus.addListener(CombatRebalance::onModifyDefaultComponents);
        NeoForge.EVENT_BUS.addListener(CombatRebalance::onItemAttributeModifiers);
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        Map<Item, CombatConfig.ItemSettings> map = new IdentityHashMap<>();
        config.items().forEach((id, settings) -> {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return;
            BuiltInRegistries.ITEM.getOptional(rl).ifPresent(item -> map.put(item, settings));
        });
        byItem = map;
        LOG.info("[{}] Rebalancing {} of {} configured items", MOD_ID, map.size(), config.items().size());
    }

    private static CombatConfig.ItemSettings settingsFor(Item item) {
        Map<Item, CombatConfig.ItemSettings> map = byItem;
        if (map != null) return map.get(item);
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        return config.items().get(key.toString());
    }

    // Durability: each item's default max damage, set once at startup
    private static void onModifyDefaultComponents(ModifyDefaultComponentsEvent event) {
        config.items().forEach((id, settings) -> {
            if (settings.durability() == null) return;
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl == null) return;
            BuiltInRegistries.ITEM.getOptional(rl).ifPresent(item -> {
                int durability = settings.durability();
                if (durability == CombatConfig.UNBREAKABLE) {
                    event.modify(item, builder -> builder.remove(DataComponents.MAX_DAMAGE).remove(DataComponents.DAMAGE));
                } else {
                    event.modify(item, builder -> builder.set(DataComponents.MAX_DAMAGE, durability));
                }
            });
        });
    }

    private static void onItemAttributeModifiers(ItemAttributeModifierEvent event) {
        CombatConfig.ItemSettings settings = settingsFor(event.getItemStack().getItem());
        if (settings == null || !settings.changesAttributes()) return;

        // The item's own base modifiers (from its defaults), whatever ids its mod gave them; anything added on
        // top by other code isn't in this set and is left alone
        Set<String> base = new HashSet<>();
        for (ItemAttributeModifiers.Entry d : event.getDefaultModifiers().modifiers()) {
            base.add(d.attribute().getRegisteredName() + "|" + d.modifier().id());
        }

        List<ItemAttributeModifiers.Entry> entries = List.copyOf(event.getModifiers());
        for (ItemAttributeModifiers.Entry e : entries) {
            AttributeModifier m = e.modifier();
            Holder<Attribute> attribute = e.attribute();
            String attr = attribute.getRegisteredName();
            if (!base.contains(attr + "|" + m.id())) continue;
            if (m.operation() != AttributeModifier.Operation.ADD_VALUE) continue;

            Double amount = null;
            if (attr.contains("attack_damage")) {
                if (settings.noDamage()) {
                    event.removeModifier(attribute, m.id());
                    continue;
                }
                // Configured as the tooltip shows it: the player's base 1 plus the item's bonus
                if (settings.damage() != null) amount = settings.damage() - 1;
            } else if (attr.contains("attack_speed")) {
                if (settings.noDamage()) event.removeModifier(attribute, m.id());
                continue;
            } else if (settings.armor() != null && attr.endsWith("generic.armor")) {
                amount = settings.armor();
            } else if (settings.toughness() != null && attr.contains("armor_toughness")) {
                amount = settings.toughness();
            } else if (settings.knockbackResistance() != null && attr.contains("knockback_resistance")) {
                amount = settings.knockbackResistance();
            }
            if (amount != null) {
                event.replaceModifier(attribute, new AttributeModifier(m.id(), amount, m.operation()), e.slot());
            }
        }
    }
}
