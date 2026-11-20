package com.kishku7.irongolemspawnchecker.net;

import com.kishku7.irongolemspawnchecker.IronGolemSpawnChecker;
import com.kishku7.irongolemspawnchecker.check.CheckResult;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Entry;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Village;
import com.kishku7.irongolemspawnchecker.check.Reason;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the answer for one villager. Sent only in reply to a request, so a client that
 * never asks (or has no mod) never receives it.
 */
public record CheckReplyPayload(CheckResult result) implements CustomPacketPayload {

    public static final Type<CheckReplyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IronGolemSpawnChecker.MOD_ID, "check_reply"));

    /** Hard cap on reasons read from the wire, so a bad packet cannot allocate a huge list. */
    private static final int MAX_REASONS = 16;

    public static final StreamCodec<RegistryFriendlyByteBuf, CheckReplyPayload> CODEC =
            StreamCodec.of(CheckReplyPayload::write, CheckReplyPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, CheckReplyPayload payload) {
        CheckResult r = payload.result();
        buf.writeVarInt(r.entityId());
        buf.writeVarInt(r.reasons().size());
        for (Entry e : r.reasons()) {
            buf.writeVarInt(e.reason().wireId());
            buf.writeVarInt(e.param());
        }
        Village v = r.village();
        buf.writeBoolean(v.known());
        if (v.known()) {
            buf.writeVarInt(v.rank());
            buf.writeVarInt(v.total());
            buf.writeVarInt(v.chunksX());
            buf.writeVarInt(v.chunksZ());
            buf.writeInt(v.minX());
            buf.writeInt(v.minZ());
            buf.writeInt(v.maxX());
            buf.writeInt(v.maxZ());
        }
    }

    private static CheckReplyPayload read(RegistryFriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        int count = Math.min(buf.readVarInt(), MAX_REASONS);
        List<Entry> reasons = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Reason reason = Reason.fromWire(buf.readVarInt());
            int param = buf.readVarInt();
            if (reason != null) {
                reasons.add(new Entry(reason, param));
            }
        }
        Village village = Village.unknown();
        if (buf.readBoolean()) {
            village = new Village(true, buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }
        return new CheckReplyPayload(new CheckResult(entityId, reasons, village));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
