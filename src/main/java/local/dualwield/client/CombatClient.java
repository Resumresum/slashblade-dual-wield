package local.dualwield.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import local.dualwield.BladeTypes;
import local.dualwield.CombatPackets;
import local.dualwield.DualWieldCombat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public final class CombatClient implements ClientModInitializer {
    private static final long SWING_NANOS = 300_000_000L;
    private static final Map<Integer, Swing> OFFHAND_SWINGS = new HashMap<>();
    private static final Map<Integer, Long> GUARDS = new HashMap<>();
    private static final long GUARD_NANOS = 1_100_000_000L;
    private static final long SHORT_ART_DRAW_NANOS = 450_000_000L;
    private static long shortArtStart;
    private static boolean locked;
    private static float startYaw;

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.parse("slashblade_dual_wield:actions"));
        var action = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.slashblade_dual_wield.action",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientPlayNetworking.registerGlobalReceiver(CombatPackets.Status.TYPE, (status, context) -> {
            locked = status.active();
            startYaw = status.yaw();
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatPackets.OffhandSwing.TYPE, (swing, context) -> {
            long now = System.nanoTime();
            if (swing.animation() == CombatPackets.SHORT_ART_DRAW) {
                shortArtStart = now;
                return;
            }
            OFFHAND_SWINGS.values().removeIf(previous -> now - previous.start >= SWING_NANOS);
            GUARDS.values().removeIf(start -> now - start >= GUARD_NANOS);
            if (swing.animation() == CombatPackets.GUARD) {
                OFFHAND_SWINGS.remove(swing.entityId());
                GUARDS.put(swing.entityId(), now);
            } else {
                GUARDS.remove(swing.entityId());
                if (swing.animation() == CombatPackets.SWING || swing.animation() == CombatPackets.FOLLOW_UP) {
                    OFFHAND_SWINGS.put(swing.entityId(), new Swing(now, swing.animation() == CombatPackets.FOLLOW_UP));
                }
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            locked = false;
            shortArtStart = 0;
            OFFHAND_SWINGS.clear();
            GUARDS.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (action.consumeClick()) {
                if (client.player != null && client.screen == null && !locked
                        && BladeTypes.hasUsablePair(client.player)
                        && ClientPlayNetworking.canSend(CombatPackets.Press.TYPE)) {
                    ClientPlayNetworking.send(new CombatPackets.Press(CombatPackets.ACTION));
                }
            }
            if (locked && client.player != null) {
                client.player.setYRot(DualWieldCombat.clampYaw(client.player.getYRot(), startYaw));
            }
        });
    }

    public static boolean locked() {
        return locked;
    }

    public static float shortArtDrawProgress() {
        if (shortArtStart == 0) return -1f;
        long elapsed = System.nanoTime() - shortArtStart;
        return elapsed < SHORT_ART_DRAW_NANOS ? (float) elapsed / SHORT_ART_DRAW_NANOS : -1f;
    }

    public static float offhandSwingProgress(int entityId) {
        var swing = OFFHAND_SWINGS.get(entityId);
        if (swing == null) return 0f;
        long elapsed = System.nanoTime() - swing.start;
        return elapsed < SWING_NANOS ? (float) elapsed / SWING_NANOS : 0f;
    }

    public static boolean isFollowUpSwing(int entityId) {
        var swing = OFFHAND_SWINGS.get(entityId);
        return swing != null && swing.followUp && System.nanoTime() - swing.start < SWING_NANOS;
    }

    private record Swing(long start, boolean followUp) {}

    public static float guardPose(int entityId) {
        var start = GUARDS.get(entityId);
        if (start == null) return 0f;
        long elapsed = System.nanoTime() - start;
        return elapsed < GUARD_NANOS ? Math.min(1f, elapsed / 90_000_000f) : 0f;
    }
}
