"""compat.py -- the ONE source of truth for per-loader ENTRYPOINTS (D15) and the version axis.

Golem Doctor is ONE merged jar per loader and MC line: the same binary carries the
server logic (cog_sources/shared/check + net) and the client popup (cog_sources/shared/client).
The client entrypoint is the only place client classes are referenced, so a dedicated server never
loads them.

STATUS OF THE CELLS
    Fabric   26.3  IMPLEMENTED and tested  -> GolemDoctorFabric (ModInitializer) + client entrypoint
    NeoForge 26.3  FUTURE (cell template kept; entrypoint not written yet)
    Forge    1.21.11  FUTURE (cell template kept; entrypoint not written yet)
    Plugin   FUTURE (cell template kept; a Bukkit plugin can answer the same request channel)
The other loaders are deliberately NOT removed: the cell templates, the seam folders and the entry below
stay so they can be filled in without re-deriving the structure (rule: keep every platform as a feature).
Emitting a not-yet-written loader fails loudly rather than producing a jar that loads and does nothing.

Version drift today (26.3 only): none. Cog markers go into cog_sources/shared at the drift point when
older lines are added (known drift to handle then: EntityTypes vs EntityType, the Identifier rename at
1.21.11, the CustomPacketPayload boundary at 1.20.2 / 1.20.5, GuiGraphics vs GuiGraphicsExtractor).

Usage (called by scripts/cog-gen.ps1):
    python _codegen/compat.py <fabric|neoforge|forge|plugin> <gen_src_main_java_dir> <ver>
"""

import os
import sys

PKG = "com.kishku7.golemdoctor"
PKG_PATH = PKG.replace(".", "/")


def vt(ver):
    """('1.21.11' | '26.3') -> comparable int tuple."""
    return tuple(int(x) for x in ver.split("-")[0].split("."))


# ---------------------------------------------------------------------------------------------
# VERSION DRIFT HELPERS (called from Cog blocks in cog_sources/shared; ver = the cell MC version).
# Surveyed against the decompiled sources 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, 26.3, 26.4-snapshot-8/10.
# The vanilla golem rules the check re-reads (spawnGolemIfNeeded 5 / panic 3, wantsToSpawnGolem,
# LAST_SLEPT window 24000, GOLEM_DETECTED_RECENTLY 599, placement 10 tries +-8 xz +-6 y, spawn obstruction)
# are the SAME in every one of those versions: only the names below move.
#   EntityType (class holding IRON_GOLEM)  1.21.11, 26.1.x  -> EntityType ;  26.2+ -> EntityTypes
#   Fabric API payload registry            1.21.11 -> playC2S/playS2C ; 26.1+ -> serverboundPlay/clientboundPlay
#   Fabric API HudElement callback         up to 26.3 -> (graphics, tickDelta) ; 26.4+ -> (graphics)
#   GUI draw class                         1.21.11 -> GuiGraphics (drawString, renderOutline)
#                                          26.1+   -> GuiGraphicsExtractor (text, outline)
# ---------------------------------------------------------------------------------------------

def entity_types(ver):
    """Class that holds the IRON_GOLEM constant."""
    return "EntityTypes" if vt(ver) >= (26, 2) else "EntityType"


def gfx_class(ver):
    return "GuiGraphicsExtractor" if vt(ver) >= (26, 1) else "GuiGraphics"


def entity_types_import(cog, ver):
    cog.outl("import net.minecraft.world.entity.{0};".format(entity_types(ver)))


def entity_types_create(cog, ver):
    cog.outl("IronGolem golem = {0}.IRON_GOLEM.create(level, null, search.immutable(),".format(entity_types(ver)))


def gfx_import(cog, ver):
    cog.outl("import net.minecraft.client.gui.{0};".format(gfx_class(ver)))


def gfx_render_signature(cog, ver):
    cog.outl("public static void render({0} graphics) {{".format(gfx_class(ver)))


def gfx_outline(cog, ver):
    name = "outline" if vt(ver) >= (26, 1) else "renderOutline"
    cog.outl("graphics.{0}(x, y, boxW, boxH, BORDER);".format(name))


def gfx_text(cog, ver):
    name = "text" if vt(ver) >= (26, 1) else "drawString"
    cog.outl("graphics.{0}(font, l.text, x + (boxW - font.width(l.text)) / 2, ty, l.color);".format(name))


def payload_registry(cog, ver):
    # Fabric API networking: playC2S()/playS2C() on 1.21.11, serverboundPlay()/clientboundPlay() from 26.1.
    c2s, s2c = ("playC2S", "playS2C") if vt(ver) < (26, 1) else ("serverboundPlay", "clientboundPlay")
    cog.outl("PayloadTypeRegistry.{0}().register(CheckRequestPayload.TYPE, CheckRequestPayload.CODEC);".format(c2s))
    cog.outl("PayloadTypeRegistry.{0}().register(CheckReplyPayload.TYPE, CheckReplyPayload.CODEC);".format(s2c))


def hud_lambda(ver):
    # Fabric API HudElement: render(graphics, deltaTracker) up to the 26.3 line; one argument from the 26.4 line.
    if vt(ver) >= (26, 4):
        return "graphics -> CheckClient.render(graphics));"
    return "(graphics, tickDelta) -> CheckClient.render(graphics));"


_FABRIC_MAIN = '''package com.kishku7.golemdoctor;

import com.kishku7.golemdoctor.net.CheckCommand;
import com.kishku7.golemdoctor.net.CheckerNet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

/** Golem Doctor - Fabric common entrypoint (runs on dedicated servers and clients). */
public class GolemDoctorFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        CheckerNet.registerTypes();
        CheckerNet.registerServer();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                CheckCommand.register(dispatcher));
    }
}
'''

_FABRIC_CLIENT = '''package com.kishku7.golemdoctor.client;

import com.kishku7.golemdoctor.GolemDoctor;
import com.kishku7.golemdoctor.net.CheckReplyPayload;
import com.kishku7.golemdoctor.net.CheckRequestPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.resources.Identifier;

/** Golem Doctor - Fabric client entrypoint: request while pointing, show the reply. */
public final class GolemDoctorFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CheckClient.SENDER = ClientPlayNetworking::send;
        CheckClient.CAN_SEND = () -> ClientPlayNetworking.canSend(CheckRequestPayload.TYPE);

        ClientPlayNetworking.registerGlobalReceiver(CheckReplyPayload.TYPE, (payload, context) ->
                context.client().execute(() -> CheckClient.onReply(payload.result())));

        ClientTickEvents.END_CLIENT_TICK.register(CheckClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> CheckClient.reset());

        HudElementRegistry.attachElementBefore(VanillaHudElements.HOTBAR,
                Identifier.fromNamespaceAndPath(GolemDoctor.MOD_ID, "popup"),
                __HUD_LAMBDA__
    }
}
'''

_FILES = {
    "fabric": {
        PKG_PATH + "/GolemDoctorFabric.java": _FABRIC_MAIN,
        PKG_PATH + "/client/GolemDoctorFabricClient.java": _FABRIC_CLIENT,
    },
}

_FUTURE = ("neoforge", "forge", "plugin")


def entrypoint_files(loader):
    loader = loader.lower()
    if loader in _FUTURE:
        raise SystemExit(
            "{0} cell is a FUTURE platform for this mod: the cell template is kept but its entrypoint is "
            "not written yet (see the status block at the top of compat.py).".format(loader))
    if loader not in _FILES:
        raise SystemExit("unknown loader: {0} (fabric|neoforge|forge|plugin)".format(loader))
    return _FILES[loader]


def emit(loader, gen_java_dir, ver=None):
    for rel, text in entrypoint_files(loader).items():
        dest = os.path.join(gen_java_dir, rel.replace("/", os.sep))
        os.makedirs(os.path.dirname(dest), exist_ok=True)
        if ver:
            text = text.replace("__HUD_LAMBDA__", hud_lambda(ver))
        with open(dest, "w", encoding="utf-8", newline="\n") as f:
            f.write(text)
        print("emit entrypoint: " + rel)


if __name__ == "__main__":
    if len(sys.argv) not in (3, 4):
        raise SystemExit("usage: python compat.py <fabric|neoforge|forge|plugin> <gen_src_main_java_dir> [ver]")
    _ver = sys.argv[3] if len(sys.argv) == 4 else None
    emit(sys.argv[1], sys.argv[2], _ver)
