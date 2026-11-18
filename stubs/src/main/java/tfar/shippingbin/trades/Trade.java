package tfar.shippingbin.trades;

import java.util.Optional;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.item.ItemStack;
import tfar.shippingbin.IngredientAndCount;

/** Stub of Shipping Bin 1 (1.21.1). */
public record Trade(IngredientAndCount input, ItemStack output, Optional<Holder<Attribute>> attribute) {
}
