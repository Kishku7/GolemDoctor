package com.kishku7.irongolemspawnchecker.net;

import com.kishku7.irongolemspawnchecker.IronGolemSpawnChecker;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "tell me why this villager is not making iron golems". */
public record CheckRequestPayload(int entityId) implements CustomPacketPayload {

    public static final Type<CheckRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IronGolemSpawnChecker.MOD_ID, "check_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CheckRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, CheckRequestPayload::entityId, CheckRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
