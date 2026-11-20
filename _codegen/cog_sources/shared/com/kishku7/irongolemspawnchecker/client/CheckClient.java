package com.kishku7.irongolemspawnchecker.client;

import com.kishku7.irongolemspawnchecker.IronGolemSpawnChecker;
import com.kishku7.irongolemspawnchecker.check.CheckResult;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Entry;
import com.kishku7.irongolemspawnchecker.check.CheckResult.Village;
import com.kishku7.irongolemspawnchecker.net.CheckRequestPayload;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int PANEL = 0xC0101010;
    private static final int BORDER = 0xFF505050;

    private static volatile CheckResult current;
    private static int lastTargetId = -1;
    private static int cooldown;

    /** Called every client tick. */
    public static void tick(Minecraft mc) {
        Villager target = targetVillager(mc);
        if (target == null || !CAN_SEND.getAsBoolean()) {
            current = null;
            lastTargetId = -1;
            cooldown = 0;
            return;
        }
        if (target.getId() != lastTargetId) {
            current = null;
            lastTargetId = target.getId();
            cooldown = 0;
        }
        if (cooldown-- <= 0) {
            SENDER.accept(new CheckRequestPayload(target.getId()));
            cooldown = IronGolemSpawnChecker.REFRESH_TICKS;
        }
    }

    /** Called on the client thread with a server reply. */
    public static void onReply(CheckResult result) {
        current = result;
    }

    /** Call when the player leaves a world so a stale answer cannot show in the next one. */
    public static void reset() {
        current = null;
        lastTargetId = -1;
        cooldown = 0;
    }

    private static Villager targetVillager(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return null;
        }
        Entity picked = mc.crosshairPickEntity;
        return picked instanceof Villager v && v.isAlive() ? v : null;
    }

    /** HUD element: draws the panel when there is a fresh answer for the villager under the crosshair. */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        CheckResult result = current;
        Villager target = targetVillager(mc);
        if (result == null || target == null || target.getId() != result.entityId()) {
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
        graphics.outline(x, y, boxW, boxH, BORDER);
        int ty = y + padding;
        for (Line l : lines) {
            graphics.text(font, l.text, x + (boxW - font.width(l.text)) / 2, ty, l.color);
            ty += lineHeight;
        }
    }

    private record Line(String text, int color) { }

    static List<Line> buildLines(CheckResult result) {
        List<Line> lines = new ArrayList<>();
        Village v = result.village();
        if (v.known() && v.rank() > 0) {
            lines.add(new Line("Villager " + v.rank() + " of " + v.total(), WHITE));
        } else {
            lines.add(new Line("Villager", WHITE));
        }

        if (result.reasons().isEmpty()) {
            lines.add(new Line("SHOULD SPAWN", GREEN));
        } else {
            for (Entry e : result.reasons()) {
                lines.add(new Line(e.text(), RED));
            }
        }

        if (v.known()) {
            lines.add(new Line("Village " + v.chunksX() + "x" + v.chunksZ() + " chunks", GRAY));
            lines.add(new Line("x " + v.minX() + " to " + v.maxX() + "   z " + v.minZ() + " to " + v.maxZ(), GRAY));
        }
        return lines;
    }
}
