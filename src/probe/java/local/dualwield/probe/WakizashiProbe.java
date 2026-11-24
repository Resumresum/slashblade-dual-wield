package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import cn.sh1rocu.slashblade.api.extension.EntityExtension;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import local.dualwield.BladeTypes;
import local.dualwield.FollowUp;
import local.dualwield.client.CombatClient;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.capability.inputstate.CapabilityInputState;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.client.SlashBladeKeyMappings;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class WakizashiProbe {
    private final ServerPlayer player;
    private final ItemStack blade;
    private final Zombie target;
    private final List<String> results;
    private final List<Long> hits = new ArrayList<>();
    private final List<Identifier> hitCombos = new ArrayList<>();
    private final List<String> thrustTrace = new ArrayList<>();
    private final long start;
    private volatile int phase;
    private int lastInput = -1, clientWait, chargeTicks, thrustFrames;
    private volatile boolean sawThrustPose;
    private volatile boolean sawArtDraw;
    private volatile boolean dashReady;
    private long dashTick, lastCut;

    WakizashiProbe(ServerPlayer player, ItemStack blade, Zombie target, List<String> results) {
        this.player = player;
        this.blade = blade;
        this.target = target;
        this.results = results;
        this.start = player.level().getGameTime();
        player.setItemInHand(InteractionHand.MAIN_HAND, blade);
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        player.setYRot(0);
        player.setXRot(0);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
        place(1.5);
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (phase <= 5 && entity == target && source.getEntity() == player && taken > 0 && !blocked) {
                // Earlier regression cases can leave a projectile alive nearby.
                if (!FollowUp.suppressesKnockback(target)) return;
                hits.add(player.level().getGameTime());
                hitCombos.add(CapabilitySlashBlade.getBladeState(blade).orElseThrow().getComboSeq());
            }
        });
    }

    boolean tick() {
        long now = player.level().getGameTime();
        if (now - start >= 150) {
            var input = CapabilityInputState.INPUT_STATE.maybeGet(player).orElseThrow();
            throw new AssertionError("main wakizashi timed out at phase " + phase + " with hits " + hits
                + ", onGround=" + player.onGround() + ", inputs=" + input.getCommands() + ", thrust=" + thrustTrace);
        }
        var state = CapabilitySlashBlade.getBladeState(blade).orElseThrow();
        if (phase < 3 && hits.size() == phase + 1) {
            var expected = List.of("short_cut", "short_return", "short_finish").get(phase);
            var combo = hitCombos.getLast();
            require(combo.equals(Identifier.parse("slashblade_dual_wield:" + expected)),
                "main wakizashi did not use " + expected + ": " + combo);
            phase++;
            place(1.5);
            lastCut = now;
        }
        if (phase == 3 && !dashReady && now - lastCut >= 6) {
            place(9);
            player.setOnGround(true);
            dashReady = true;
        }
        if (phase == 3 && dashReady && ((EntityExtension) player).sb$getPersistentData()
                .getIntOr(SlayerStyleArts.AVOID_COUNTER_PATH, 0) > 0) {
            dashTick = now;
            place(1.5);
            phase = 4;
        }
        if (phase == 4 && now - dashTick <= 14) {
            thrustTrace.add(now + "/" + state.getLastActionTime() + "/"
                + Math.round((target.getZ() - player.getZ()) * 100) + "/" + hits.size());
        }
        if (phase == 4 && hits.size() >= 6 && now - hits.getLast() >= 4) {
            long first = hits.get(3), second = hits.get(4), third = hits.get(5);
            require(hits.size() == 6 && second - first == 3 && third - second == 3 && first - dashTick <= 10,
                "one click did not produce exactly three equally spaced thrusts: " + hits);
            require(hitCombos.subList(3, 6).stream().allMatch(Identifier.parse("slashblade_dual_wield:short_thrust")::equals),
                "the three hits did not come from thrusts: " + hitCombos);
            require(sawThrustPose, "client never received the thrust animation");
            require(target.distanceTo(player) < 2.5, "the thrusts pushed the target out of range");
            results.add("PASS: short main left-click three-cut sequence; real V dash + one left click landed three equally spaced thrusts without knockback and sent the thrust pose");
            place(1.5);
            phase = 5;
        }
        if (phase == 5 && hits.size() == 7) {
            require(hitCombos.getLast().equals(Identifier.parse("slashblade_dual_wield:short_cut")),
                "the next left click after thrust did not restart the quick-cut chain");
            player.setHealth(5);
            phase = 6;
        }
        if (phase == 6 && player.getHealth() == player.getMaxHealth()) {
            require(state.getProudSoulCount() == 184, "the main-hand blade did not pay once for its own charged art");
            if (!sawArtDraw) return false;
            phase = 7;
            results.add("PASS: the next left click restarted the quick-cut chain, and charged First Aid still cast and sent the short blade draw cue");
            return true;
        }
        return false;
    }

    void clientTick(Minecraft client) {
        if (!BladeTypes.isWakizashi(client.player.getMainHandItem()) || !client.player.getOffhandItem().isEmpty()) return;
        int step = phase;
        if (step == 6 && CombatClient.shortArtDrawProgress() >= 0f) sawArtDraw = true;
        var state = CapabilitySlashBlade.getBladeState(client.player.getMainHandItem()).orElseThrow();
        if (step == 4 && !sawThrustPose && state.getComboSeq().equals(Identifier.parse("slashblade_dual_wield:short_thrust"))
                && ++thrustFrames == 2) {
            sawThrustPose = true;
            Screenshot.grab(new File(System.getProperty("dualwield.probe.evidence")), "main-wakizashi-thrust.png",
                client.getMainRenderTarget(), 1, ignored -> {});
        }
        if (step == 6 && lastInput == 6 && --chargeTicks == 0) client.options.keyUse.setDown(false);
        if (step == lastInput || step >= 7 || (step == 3 && !dashReady)) return;
        if (step == 3 && clientWait == 0) client.options.keyUp.setDown(true);
        if (step == 4) {
            client.options.keyUp.setDown(false);
            SlashBladeKeyMappings.KEY_SPECIAL_MOVE.setDown(false);
        }
        if (++clientWait < (step == 4 ? 2 : 6)) return;
        lastInput = step;
        clientWait = 0;
        if (step == 3) {
            SlashBladeKeyMappings.KEY_SPECIAL_MOVE.setDown(true);
        } else if (step == 6) {
            chargeTicks = 32;
            client.options.keyUse.setDown(true);
            KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(1));
        } else {
            client.options.keyUp.setDown(false);
            SlashBladeKeyMappings.KEY_SPECIAL_MOVE.setDown(false);
            KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(0));
        }
    }

    private void place(double z) {
        target.setPos(player.getX(), player.getY(), player.getZ() + z);
        target.setDeltaMovement(Vec3.ZERO);
        target.invulnerableTime = 0;
    }
}
