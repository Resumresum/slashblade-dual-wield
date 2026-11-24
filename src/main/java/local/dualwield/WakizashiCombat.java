package local.dualwield;

import java.util.Map;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public final class WakizashiCombat {
    private static final int CUT_GAP = 4;
    private static final int CHAIN_WINDOW = 14;
    private static final int DASH_WINDOW = 12;
    private static final int THRUST_GAP = 3;
    private static final int THRUST_COUNT = 3;
    private static final double CUT_REACH = 2.6;
    private static final double THRUST_REACH = 3.0;
    private static final float[] CUT_DAMAGE = {0.44f, 0.44f, 0.55f};
    private static final Identifier CUT = id("short_cut");
    private static final Identifier RETURN = id("short_return");
    private static final Identifier FINISH = id("short_finish");
    private static final Identifier THRUST = id("short_thrust");
    private static final Identifier[] CUT_MOTIONS = {CUT, RETURN, FINISH};
    private static final Map<ServerPlayer, Action> ACTIONS = new WeakHashMap<>();

    private WakizashiCombat() {}

    static void register() {
        registerPose(CUT, DefaultResources.ExMotionLocation, 1, 10);
        registerPose(RETURN, DefaultResources.ExMotionLocation, 100, 109);
        registerPose(FINISH, DefaultResources.testLocation, 6, 13);
        registerPose(THRUST, DefaultResources.testLocation, 6, 11);
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(WakizashiCombat::tick));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ACTIONS.remove(handler.player));
    }

    private static void registerPose(Identifier id, Identifier motion, int start, int end) {
        Registry.register(ComboStateRegistry.COMBO_STATE, id, ComboState.Builder.newInstance()
            .motionLoc(motion).startAndEnd(start, end)
            .nextOfTimeout(user -> ComboStateRegistry.getId(ComboStateRegistry.NONE)).build());
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(DualWield.ID, path);
    }

    public static boolean canAttack(LivingEntity player) {
        var main = player.getMainHandItem();
        return player.isAlive() && !player.isSpectator() && BladeTypes.isWakizashi(main) && BladeTypes.isUsable(main);
    }

    public static void onNativeDash(ServerPlayer player) {
        if (!canAttack(player)) return;
        var action = action(player);
        long now = player.level().getGameTime();
        action.dashUntil = now + DASH_WINDOW;
        action.cut = 0;
    }

    static void attack(ServerPlayer player) {
        if (!canAttack(player) || player.isUsingItem()) return;
        var action = action(player);
        long now = player.level().getGameTime();
        if (action.remainingThrusts > 0) return;
        if (now <= action.dashUntil) {
            action.dashUntil = -1;
            action.cut = 0;
            action.remainingThrusts = THRUST_COUNT;
            action.nextThrust = now;
            action.nextAttack = now + THRUST_COUNT * THRUST_GAP;
            action.creditedHit = false;
            action.thrustTarget = null;
            var velocity = player.getDeltaMovement();
            player.setDeltaMovement(0, velocity.y, 0);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
            thrust(player, action, now);
            return;
        }
        if (now < action.nextAttack) return;
        if (now > action.chainUntil) action.cut = 0;
        int cut = action.cut;
        action.cut = (cut + 1) % CUT_MOTIONS.length;
        action.chainUntil = now + CHAIN_WINDOW;
        action.nextAttack = now + CUT_GAP;
        action.creditedHit = false;
        strike(player, action, CUT_MOTIONS[cut], CUT_DAMAGE[cut], CUT_REACH, cut == 2, cut == 1 ? -1 : 1);
    }

    private static Action action(ServerPlayer player) {
        return ACTIONS.compute(player, (key, current) -> current != null && current.matches(player)
            ? current : new Action(player.level(), player.getMainHandItem()));
    }

    private static void tick(ServerPlayer player) {
        var action = ACTIONS.get(player);
        if (action == null) return;
        long now = player.level().getGameTime();
        if (!action.matches(player) || (action.remainingThrusts == 0 && now > action.chainUntil
                && now > action.dashUntil && now >= action.nextAttack)) {
            ACTIONS.remove(player);
            return;
        }
        if (action.remainingThrusts > 0 && now >= action.nextThrust) {
            thrust(player, action, now);
        }
    }

    private static void thrust(ServerPlayer player, Action action, long now) {
        action.remainingThrusts--;
        action.nextThrust = now + THRUST_GAP;
        strike(player, action, THRUST, 0.23f, THRUST_REACH, true, 1);
    }

    private static void strike(ServerPlayer player, Action action, Identifier pose, float ratio, double reach,
            boolean narrow, int direction) {
        var state = CapabilitySlashBlade.getBladeState(action.blade).orElseThrow();
        state.updateComboSeq(player, pose);
        Vec3 look = player.getLookAngle();
        Vec3 forward = new Vec3(look.x, 0, look.z).normalize();
        Vec3 origin = player.position().add(0, player.getBbHeight() / 2, 0);
        boolean sequence = pose.equals(THRUST);
        var locked = sequence && action.thrustTarget != null && action.thrustTarget.isAlive() ? action.thrustTarget : null;
        CombatTargeting.area(player, reach, hits -> {
            LivingEntity closest = null;
            double nearest = Double.POSITIVE_INFINITY;
            for (var hit : hits) {
                var target = hit.target();
                if (locked != null && target != locked) continue;
                var bounds = hit.physical().getBoundingBox();
                double distance = bounds.distanceToSqr(origin);
                var delta = bounds.getCenter().subtract(origin).multiply(1, 0, 1);
                if (distance > reach * reach || (locked == null && delta.lengthSqr() > 0.01
                        && forward.dot(delta.normalize()) < (narrow ? 0.85 : 0.30))) continue;
                if (!narrow) {
                    damage(player, action, target, ratio);
                } else if (distance < nearest) {
                    nearest = distance;
                    closest = target;
                }
            }
            if (closest != null && damage(player, action, closest, ratio) && sequence) action.thrustTarget = closest;
            return null;
        });
        player.swing(InteractionHand.MAIN_HAND, true);
        showStrike(player, state.getColorCode(), forward, narrow, direction);
    }

    private static boolean damage(ServerPlayer player, Action action, LivingEntity target, float ratio) {
        if (!action.matches(player)) return false;
        var source = player.damageSources().playerAttack(player);
        float damage = (float) AttackHelper.calculateTotalDamage(player, target, ratio,
            AttackHelper.isCriticalHit(player, target), source);
        if (damage <= 0 || !FollowUp.damageWithoutKnockback(player, target, damage, !action.creditedHit)) return false;
        if (!action.creditedHit) {
            action.creditedHit = true;
            AttackHelper.handleEnchantmentsAndDurability(player, target, source);
        }
        return true;
    }

    private static void showStrike(ServerPlayer player, int color, Vec3 forward, boolean narrow, int direction) {
        var level = player.level();
        var origin = player.position().add(0, player.getEyeHeight() * 0.72, 0);
        var dust = new DustParticleOptions(color, 0.85f);
        var side = new Vec3(-forward.z, 0, forward.x);
        for (int i = -3; i <= 3; i++) {
            Vec3 point = narrow ? origin.add(forward.scale(1.3 + i * 0.22))
                : origin.add(forward.scale(1.7 - i * i * 0.045)).add(side.scale(i * 0.30))
                    .add(0, i * 0.05 * direction, 0);
            level.sendParticles(dust, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
        if (narrow) AttackManager.playPiercingSoundAction(player);
        else level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5f, 1.3f);
    }

    private static final class Action {
        final ServerLevel level;
        final ItemStack blade;
        int cut, remainingThrusts;
        long chainUntil = -1, dashUntil = -1, nextAttack, nextThrust;
        boolean creditedHit;
        LivingEntity thrustTarget;

        Action(ServerLevel level, ItemStack blade) {
            this.level = level;
            this.blade = blade;
        }

        boolean matches(ServerPlayer player) {
            return level == player.level() && blade == player.getMainHandItem() && canAttack(player);
        }
    }
}
