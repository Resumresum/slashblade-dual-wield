package local.dualwield.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.HashMap;
import java.util.Map;
import local.dualwield.BladeTypes;
import local.dualwield.CombatPackets;
import local.dualwield.BladeMotion;
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
    private static final Map<Integer, Stroke[]> STROKES = new HashMap<>();
    private static final Map<Integer, Long> BURSTS = new HashMap<>();
    private static Object strokeLevel;
    private static boolean locked;
    private static float startYaw;

    @Override
    public void onInitializeClient() {
        var category = KeyMapping.Category.register(Identifier.parse("slashblade_dual_wield:actions"));
        var action = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.slashblade_dual_wield.action",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, category));
        ClientPlayNetworking.registerGlobalReceiver(CombatPackets.Status.TYPE, (status, context) -> {
            refreshWorld(context.client());
            long now=System.nanoTime();
            if(status.active()) BURSTS.put(status.entityId(), now+5_000_000_000L);
            else {
                BURSTS.remove(status.entityId());
                var pair=STROKES.get(status.entityId());
                if(pair!=null) for(var stroke:pair) if(stroke!=null && stroke.kind()==BladeMotion.BURST) stroke.released=now;
            }
            if(context.client().player!=null && context.client().player.getId()==status.entityId()) {
                locked = status.active();
                startYaw = status.yaw();
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatPackets.Stroke.TYPE, (stroke, context) -> {
            refreshWorld(context.client());
            var player = context.client().level == null ? null : context.client().level.getEntity(stroke.entityId());
            if (!(player instanceof net.minecraft.world.entity.LivingEntity living)) return;
            STROKES.values().removeIf(pair -> expired(pair[0]) && expired(pair[1]));
            var pair = STROKES.computeIfAbsent(stroke.entityId(), ignored -> new Stroke[2]);
            var previous=CombatClient.stroke(stroke.entityId(),stroke.offhand());
            pair[stroke.offhand() ? 1 : 0] = new Stroke(System.nanoTime(), stroke.kind(), stroke.beat(),
                stroke.color(), living, previous==null ? null : previous.pose(previous.progress()),previous==null ? 0f : previous.weight());
        });
        ClientPlayNetworking.registerGlobalReceiver(CombatPackets.OffhandSwing.TYPE, (swing, context) -> {
            refreshWorld(context.client());
            long now = System.nanoTime();
            if (swing.animation() == CombatPackets.SHORT_ART_DRAW || swing.animation() == CombatPackets.MAIN_ART) {
                var pair = STROKES.get(swing.entityId());
                if (pair != null) pair[0] = null;
                if(swing.animation()==CombatPackets.SHORT_ART_DRAW) shortArtStart = now;
                return;
            }
            // Native guard/counter/SA messages replace a cosmetic offhand cut immediately.
            var pair = STROKES.get(swing.entityId());
            if (pair != null) pair[1] = null;
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
            STROKES.clear();
            BURSTS.clear();
            strokeLevel=null;
            BladeStrokeRender.clearMounts();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            refreshWorld(client);
            BURSTS.entrySet().removeIf(entry -> entry.getValue()<=System.nanoTime()
                || client.level==null || client.level.getEntity(entry.getKey())==null);
            STROKES.values().removeIf(pair -> {
                for(int i=0;i<2;i++) if(pair[i]!=null && (expired(pair[i]) || pair[i].owner.level()!=client.level || !pair[i].owner.isAlive())) pair[i]=null;
                return pair[0]==null && pair[1]==null;
            });
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

    private static void refreshWorld(Minecraft client) {
        if(strokeLevel==client.level) return;
        STROKES.clear(); BURSTS.clear(); OFFHAND_SWINGS.clear(); GUARDS.clear();
        shortArtStart=0; locked=false;
        BladeStrokeRender.clearMounts();
        strokeLevel=client.level;
    }

    private static boolean bursting(int entityId) {
        return BURSTS.getOrDefault(entityId,0L)>System.nanoTime();
    }

    private static boolean expired(Stroke stroke) { return stroke == null || stroke.weight() <= 0f && stroke.progress()>1f; }

    public static Stroke stroke(int entityId, boolean offhand) {
        var pair = STROKES.get(entityId);
        if (pair == null) return null;
        var stroke = pair[offhand ? 1 : 0];
        if (expired(stroke)) return null;
        var client = Minecraft.getInstance();
        var entity = client.level == null ? null : client.level.getEntity(entityId);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()
                || living != stroke.owner
                || !sameBlade(stroke.main, living.getMainHandItem())
                || !sameBlade(stroke.off, living.getOffhandItem())) return null;
        if (living.isUsingItem() && living.getUsedItemHand()
                == (offhand ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND)) {
            pair[offhand ? 1 : 0] = null;
            return null;
        }
        return stroke;
    }

    private static boolean sameBlade(net.minecraft.world.item.ItemStack a, net.minecraft.world.item.ItemStack b) {
        if (!a.is(b.getItem())) return false;
        var before = mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade.getBladeState(a);
        var after = mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade.getBladeState(b);
        return before.isEmpty() ? after.isEmpty() : after.isPresent()
            && before.get().getModel().equals(after.get().getModel())
            && BladeTypes.isCraftedWakizashi(a) == BladeTypes.isCraftedWakizashi(b);
    }

    public static final class Stroke {
        private final long start;
        private final int kind,beat,color;
        private final net.minecraft.world.entity.LivingEntity owner;
        private final net.minecraft.world.item.ItemStack main,off;
        private final BladeMotion.Pose previous;
        private final float initialWeight;
        private long released;
        Stroke(long start,int kind,int beat,int color,net.minecraft.world.entity.LivingEntity owner,BladeMotion.Pose previous,float initialWeight) {
            this.start=start; this.kind=kind; this.beat=beat; this.color=color; this.owner=owner; this.previous=previous;
            this.initialWeight=initialWeight;
            main=owner.getMainHandItem().copy(); off=owner.getOffhandItem().copy();
        }
        public int kind(){return kind;}
        public int beat(){return beat;}
        public int color(){return color;}
        public float progress() {
            return (System.nanoTime() - start) / (BladeMotion.durationTicks(kind, beat) * 50_000_000f);
        }
        public BladeMotion.Pose pose(float progress) {
            var frame=BladeMotion.sample(kind,beat,progress);
            float preparation=kind==BladeMotion.THRUST||kind==BladeMotion.FINISH ? .12f : BladeMotion.strikeStart(kind);
            return previous!=null && progress<preparation
                ? BladeMotion.blend(previous,frame,BladeMotion.ease(progress/preparation)) : frame;
        }
        public float weight() {
            long now=System.nanoTime();
            return BladeMotion.weight(kind,beat,(now-start)/50_000_000f,initialWeight,
                kind==BladeMotion.BURST && bursting(owner.getId()),released==0 ? -1f : (released-start)/50_000_000f);
        }
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
