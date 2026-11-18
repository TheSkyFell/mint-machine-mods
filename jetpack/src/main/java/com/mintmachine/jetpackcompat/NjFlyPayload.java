package com.mintmachine.jetpackcompat;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client to server: whether create_nj's fly key is held while a body-slot Netherite Jetpack is worn. */
public record NjFlyPayload(boolean down) implements CustomPacketPayload {
    public static final Type<NjFlyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JetpackCurioCompat.MOD_ID, "nj_fly"));

    public static final StreamCodec<ByteBuf, NjFlyPayload> STREAM_CODEC =
            ByteBufCodecs.BOOL.map(NjFlyPayload::new, NjFlyPayload::down);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
