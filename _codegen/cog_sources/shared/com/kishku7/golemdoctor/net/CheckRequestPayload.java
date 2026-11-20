package com.kishku7.golemdoctor.net;

import com.kishku7.golemdoctor.GolemDoctor;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "tell me why this villager is not making iron golems". */
public record CheckRequestPayload(int entityId) implements CustomPacketPayload {

    public static final Type<CheckRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(GolemDoctor.MOD_ID, "check_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CheckRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CheckRequestPayload::entityId, CheckRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
