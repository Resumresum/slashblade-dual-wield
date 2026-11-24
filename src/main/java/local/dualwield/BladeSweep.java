package local.dualwield;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.util.AttackHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

final class BladeSweep {
    private static final double SHORT_REACH = 2.5;
    static final double LONG_REACH = 5.0;

    private BladeSweep() {}

    static void counter(ServerPlayer player) {
        var level = player.level();
        var origin = player.position().add(0, player.getBbHeight() / 2, 0);
        CombatTargeting.area(player, SHORT_REACH, hits -> {
            for (var hit : hits) {
                var target = hit.target();
                if (hit.physical().getBoundingBox().distanceToSqr(origin) > SHORT_REACH * SHORT_REACH) continue;
                if (CombatGrowth.withOffhand(player, () -> FollowUp.damageWithoutKnockback(player, target, 2f))) {
                    target.knockback(0.18, player.getX() - target.getX(), player.getZ() - target.getZ());
                }
            }
            return null;
        });
        CombatPackets.swingOffhand(player);
        level.playSound(null, player, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.8f, 1.25f);
        int color = CapabilitySlashBlade.getBladeState(player.getOffhandItem()).orElseThrow().getColorCode();
        var particle = new DustParticleOptions(color, 1.05f);
        for (int point = 0; point < 24; point++) {
            double angle = point * Math.PI / 12;
            level.sendParticles(particle, player.getX() + Math.sin(angle) * 1.8, player.getY() + 0.9,
                player.getZ() + Math.cos(angle) * 1.8, 1, 0, 0, 0, 0);
        }
    }

    static void burst(ServerPlayer player, InteractionHand hand, int beat) {
        int holdTicks = beat >= 8 ? 24 : beat == 7 ? 12 : 8;
        sweep(player, hand, LONG_REACH, hand == InteractionHand.MAIN_HAND ? 0.52f : 0.36f, beat >= 8, holdTicks);
    }

    static void followUpCue(ServerPlayer player) {
        int color = CapabilitySlashBlade.getBladeState(player.getOffhandItem()).orElseThrow().getColorCode();
        CombatPackets.animateOffhand(player, CombatPackets.FOLLOW_UP);
        var level = player.level();
        var dust = new DustParticleOptions(color, 1.05f);
        int side = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        for (int point = 0; point < 9; point++) {
            double progress = point / 8.0;
            double angle = Math.toRadians(player.getYRot() + side * (-54 + 72 * progress));
            double radius = 1.10 + 0.45 * progress;
            level.sendParticles(dust, player.getX() - Math.sin(angle) * radius,
                player.getY() + player.getBbHeight() * (0.43 + 0.21 * progress),
                player.getZ() + Math.cos(angle) * radius, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.35f);
    }

    static void offhandSweep(ServerPlayer player) {
        sweep(player, InteractionHand.OFF_HAND, LONG_REACH, 0.65f, true, 0);
    }

    static void fallback(ServerPlayer player, InteractionHand hand) {
        sweep(player, hand, LONG_REACH, 0.24f, false, 8);
    }

    private static void sweep(ServerPlayer player, InteractionHand hand, double reach, float ratio, boolean wide,
            int holdTicks) {
        var stack = player.getItemInHand(hand);
        var state = CapabilitySlashBlade.getBladeState(stack).orElseThrow();
        var blade = (ItemSlashBlade) stack.getItem();
        double weaponDamage = blade.getDefaultAttributeModifiers(stack).modifiers().stream()
            .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
            .mapToDouble(entry -> entry.modifier().amount()).sum();
        float damage = (float) Math.max(0.5, (weaponDamage + 1 + AttackHelper.getRankBonus(player)) * ratio);
        var level = player.level();
        boolean hit = CombatTargeting.area(player, reach, hits -> {
            boolean any = false;
            for (var candidate : hits) {
                var target = candidate.target();
                if (!withinSwing(player, candidate.physical(), reach)) continue;
                int previousImmunity = target.invulnerableTime;
                target.invulnerableTime = 0;
                boolean damaged;
                try {
                    damaged = CombatTargeting.damage(target, () -> hand == InteractionHand.OFF_HAND
                        ? CombatGrowth.withOffhand(player,
                            () -> target.hurtServer(level, player.damageSources().playerAttack(player), damage))
                        : target.hurtServer(level, player.damageSources().playerAttack(player), damage));
                } finally {
                    target.invulnerableTime = Math.max(previousImmunity, target.invulnerableTime);
                }
                if (damaged) {
                    any = true;
                    if (holdTicks > 0) BurstTargetHold.hold(target, holdTicks);
                }
            }
            return any;
        });
        if (hand == InteractionHand.MAIN_HAND) {
            state.setLastActionTime(level.getGameTime());
            state.synchronizeComboSeq(player, ComboStateRegistry.getId(wide
                ? ComboStateRegistry.COMBO_A2 : ComboStateRegistry.COMBO_A1));
            if (hit) stack.hurtAndBreak(1, level, player, ItemSlashBlade.getOnBroken(stack, player));
        }
        player.swing(hand, true);
        if (hand == InteractionHand.OFF_HAND) CombatPackets.swingOffhand(player);
        showSweep(player, state.getColorCode(), reach, hand, wide);
    }

    private static boolean withinSwing(ServerPlayer player, net.minecraft.world.entity.Entity target, double reach) {
        var origin = player.position().add(0, player.getBbHeight() / 2, 0);
        var bounds = target.getBoundingBox();
        Vec3 delta = horizontal(bounds.getCenter().subtract(origin));
        Vec3 forward = horizontal(player.getLookAngle()).normalize();
        return bounds.distanceToSqr(origin) <= reach * reach && player.hasLineOfSight(target)
            && (delta.lengthSqr() < 0.01 || forward.dot(delta.normalize()) >= 0.30);
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }

    private static void showSweep(ServerPlayer player, int color, double reach, InteractionHand hand, boolean wide) {
        var level = player.level();
        var particle = new DustParticleOptions(color, wide ? 1.15f : 0.85f);
        float yaw = player.getYRot() * (float) Math.PI / 180;
        double radius = reach * (wide ? 0.63 : 0.50);
        int direction = hand == InteractionHand.MAIN_HAND ? 1 : -1;
        for (int i = -4; i <= 4; i++) {
            double angle = yaw + i * 0.20;
            level.sendParticles(particle, player.getX() - Math.sin(angle) * radius,
                player.getY() + player.getEyeHeight() * 0.70 + i * 0.045 * direction,
                player.getZ() + Math.cos(angle) * radius, 1, 0, 0, 0, 0);
        }
        level.playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
            0.52f, hand == InteractionHand.MAIN_HAND ? 0.95f : 1.25f);
    }
}
