package com.kishku7.irongolemspawnchecker.net;

import com.kishku7.irongolemspawnchecker.check.CheckResult;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Fabric packet wiring. Both payload types are registered on both sides (Fabric requires that), but
 * nothing here forces a client to have the mod: a vanilla client never sends a request, so the server
 * never sends it a reply, and a modded client only asks when the server advertises the request channel.
 */
public final class CheckerNet {
    private CheckerNet() {}

    /** Register payload types. Runs on both client and dedicated server. */
    public static void registerTypes() {
        PayloadTypeRegistry.serverboundPlay().register(CheckRequestPayload.TYPE, CheckRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(CheckReplyPayload.TYPE, CheckReplyPayload.CODEC);
    }

    /** Register the server-side request handler and the disconnect cleanup. */
    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(CheckRequestPayload.TYPE, (payload, context) -> {
            CheckResult result = CheckServer.handle(context.player(), payload.entityId());
            if (result != null) {
                ServerPlayNetworking.send(context.player(), new CheckReplyPayload(result));
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                CheckServer.forget(handler.getPlayer().getUUID()));
    }
}
