package com.kishku7.golemdoctor.client;

import com.kishku7.golemdoctor.GolemDoctor;
import com.kishku7.golemdoctor.check.CheckResult;
import com.kishku7.golemdoctor.check.CheckResult.Entry;
import com.kishku7.golemdoctor.check.CheckResult.Village;
import com.kishku7.golemdoctor.net.CheckRequestPayload;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
//[[[cog
// import sys; sys.path.insert(0, codegen); import compat
// compat.gfx_import(cog, ver)
//]]]
import net.minecraft.client.gui.GuiGraphicsExtractor;
//[[[end]]]
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Client half, loader neutral. The loader client entrypoint sets {@link #SENDER} and {@link #CAN_SEND},
 * forwards each client tick to {@link #tick}, hands replies to {@link #onReply} and draws
 * {@link #render} as a HUD element. Nothing here runs on a dedicated server.
 *
 * The popup is shown only while the crosshair is on the villager the last reply was about, and it
 * disappears on the very next frame the crosshair leaves that villager.
 */
public final class CheckClient {
    private CheckClient() {}

    /** Set by the loader: sends a payload to the server. */
    public static volatile Consumer<CustomPacketPayload> SENDER = p -> { };
    /** Set by the loader: true only when the connected server advertises our request channel. */
    public static volatile BooleanSupplier CAN_SEND = () -> false;

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int PANEL = 0xC0101010;
    private static final int BORDER = 0xFF505050;

    /** How long the popup stays after the crosshair leaves the villager (5 seconds), so a running villager can be read. */
    private static final int HOLD_TICKS = 100;

    private static volatile CheckResult current;
    private static volatile int heldId = -1;
    private static volatile long holdUntil;
    private static long clientTicks;
    private static int cooldown;

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        clientTicks++;
        if (mc.player == null || mc.level == null || !CAN_SEND.getAsBoolean()) {
            reset();
            return;
        }
        Villager target = targetVillager(mc);
        if (target != null) {
            if (target.getId() != heldId) {
                current = null;
                cooldown = 0;
            }
            heldId = target.getId();
            holdUntil = clientTicks + HOLD_TICKS;
        } else if (heldId != -1) {
            Entity held = mc.level.getEntity(heldId);
            if (clientTicks > holdUntil || !(held instanceof Villager v) || !v.isAlive()) {
                clearHeld();
                return;
            }
        } else {
            return;
        }
        if (cooldown-- <= 0) {
            SENDER.accept(new CheckRequestPayload(heldId));
            cooldown = GolemDoctor.REFRESH_TICKS;
        }
    }

    private static void clearHeld() {
        current = null;
        heldId = -1;
        cooldown = 0;
    }

    /** Called on the client thread with a server reply. */
    public static void onReply(CheckResult result) {
        CheckResult previous = current;
        current = result;
        // One line per change, not per refresh, so a long look at one villager does not flood the log.
        if (!result.equals(previous)) {
            LOGGER.info("[GolemDoctor] reply for villager {}: {} | village {}",
                    result.entityId(), result.reasons(), result.village());
        }
    }

    /** Call when the player leaves a world so a stale answer cannot show in the next one. */
    public static void reset() {
        clearHeld();
    }

    private static Villager targetVillager(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Entity picked = mc.crosshairPickEntity;
        return picked instanceof Villager v && v.isAlive() ? v : null;
    }

    /** HUD element: draws the panel when there is a fresh answer for the villager under the crosshair. */
    //[[[cog
    // compat.gfx_render_signature(cog, ver)
    //]]]
    public static void render(GuiGraphicsExtractor graphics) {
    //[[[end]]]
        Minecraft mc = Minecraft.getInstance();
        CheckResult result = current;
        if (result == null || heldId == -1 || result.entityId() != heldId) {
            return;
        }

        List<Line> lines = buildLines(result);
        Font font = mc.font;
        int width = 0;
        for (Line l : lines) {
            width = Math.max(width, font.width(l.text));
        }
        int padding = 8;
        int lineHeight = font.lineHeight + 2;
        int boxW = width + padding * 2;
        int boxH = lines.size() * lineHeight + padding * 2 - 2;
        int x = (graphics.guiWidth() - boxW) / 2;
        // Centered on the screen, just below the crosshair so the villager stays visible while aiming.
        int y = graphics.guiHeight() / 2 + 16;

        graphics.fill(x, y, x + boxW, y + boxH, PANEL);
        //[[[cog
        // compat.gfx_outline(cog, ver)
        //]]]
        graphics.outline(x, y, boxW, boxH, BORDER);
        //[[[end]]]
        int ty = y + padding;
        for (Line l : lines) {
            //[[[cog
            // compat.gfx_text(cog, ver)
            //]]]
            graphics.text(font, l.text, x + (boxW - font.width(l.text)) / 2, ty, l.color);
            //[[[end]]]
            ty += lineHeight;
        }
    }

    private record Line(String text, int color) { }

    static List<Line> buildLines(CheckResult result) {
        List<Line> lines = new ArrayList<>();
        Village v = result.village();

        // Individual: everything wrong with this one villager, all at once.
        lines.add(new Line(v.known() && v.rank() > 0
                ? "VILLAGER  " + v.rank() + " of " + v.total() : "VILLAGER", WHITE));
        boolean individualOk = true;
        for (Entry e : result.reasons()) {
            if (!e.reason().isGroup()) {
                lines.add(new Line(e.text(), RED));
                individualOk = false;
            }
        }
        if (individualOk) {
            lines.add(new Line("OK", GREEN));
        }

        // Group: both ways a golem can come about, scare first (the usual farm), then the shared blockers.
        lines.add(new Line("GROUP", WHITE));
        lines.add(new Line(result.nearby() + " in range, " + result.ready() + " ready"
                + (result.panicking() ? ", scared now" : ""), GRAY));
        lines.add(new Line(result.methodText(true), result.ready() >= CheckResult.SCARE_NEEDED ? GREEN : RED));
        lines.add(new Line(result.methodText(false), result.ready() >= CheckResult.GOSSIP_NEEDED ? GREEN : RED));
        boolean spaceOk = true;
        for (Entry e : result.reasons()) {
            if (e.reason().isGroup()) {
                lines.add(new Line(e.text(), RED));
                spaceOk = false;
            }
        }
        if (spaceOk) {
            lines.add(new Line("Spawn Space OK", GREEN));
        }
        if (v.known()) {
            lines.add(new Line("Village " + v.chunksX() + "x" + v.chunksZ() + " chunks", GRAY));
            lines.add(new Line("x " + v.minX() + " to " + v.maxX() + "   z " + v.minZ() + " to " + v.maxZ(), GRAY));
        }
        if (result.scareOk()) {
            lines.add(new Line("Should Spawn (scare)", GREEN));
        }
        if (result.gossipOk()) {
            lines.add(new Line("Should Spawn (gossip)", GREEN));
        }
        return lines;
    }
}
