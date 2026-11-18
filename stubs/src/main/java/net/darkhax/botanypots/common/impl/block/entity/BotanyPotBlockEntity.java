package net.darkhax.botanypots.common.impl.block.entity;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Stub of Botany Pots 21.1.44. The real class extends AbstractBotanyPotBlockEntity (where getSeedItem lives),
 * a RandomizableContainerBlockEntity; the JVM finds inherited methods there at run time.
 */
public abstract class BotanyPotBlockEntity extends BlockEntity {
    private BotanyPotBlockEntity() {
        super(null, null, null);
    }

    public void updateGrowthTime(float growthTime) {
        throw new AssertionError("stub");
    }

    public float growthTime() {
        throw new AssertionError("stub");
    }

    public int getRequiredGrowthTicks() {
        throw new AssertionError("stub");
    }

    public ItemStack getSeedItem() {
        throw new AssertionError("stub");
    }
}
