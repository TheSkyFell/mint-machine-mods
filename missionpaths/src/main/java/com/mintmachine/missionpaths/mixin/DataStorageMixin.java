package com.mintmachine.missionpaths.mixin;

import com.kryeit.missions.DataStorage;
import com.kryeit.missions.MissionType;
import com.kryeit.missions.config.ConfigReader;
import com.mintmachine.missionpaths.PathAssigner;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Missions 0.5.2 picks each player's missions in DataStorage: reassignActiveMissions for the weekly refresh and
 * reassignActiveMission for a reroll. Both are replaced by path-aware picking; when that returns nothing (disabled, or
 * an error), the original code runs.
 */
@Mixin(value = DataStorage.class, remap = false)
public abstract class DataStorageMixin {

    @Shadow
    private ListTag getActiveMissionsTag(UUID player) {
        throw new AssertionError();
    }

    @Inject(method = "reassignActiveMissions", at = @At("HEAD"), cancellable = true)
    private void missionsPaths$assignWeek(Map<MissionType, ConfigReader.MissionTypeConfig> missions, UUID player,
                                          CallbackInfo ci) {
        List<CompoundTag> week = PathAssigner.week(missions);
        if (week == null) return;
        ListTag list = this.getActiveMissionsTag(player);
        list.clear();
        list.addAll(week);
        ci.cancel();
    }

    @Inject(method = "reassignActiveMission", at = @At("HEAD"), cancellable = true)
    private void missionsPaths$reroll(Map<MissionType, ConfigReader.MissionTypeConfig> missions, UUID player, int index,
                                      CallbackInfo ci) {
        ListTag list = this.getActiveMissionsTag(player);
        CompoundTag replacement = PathAssigner.reroll(missions, list, index);
        if (replacement == null) return;
        list.set(index, replacement);
        ci.cancel();
    }
}
