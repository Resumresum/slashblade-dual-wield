package local.dualwield;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.util.AttackHelper;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
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
                    impact(player, hit.physical());
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
        int holdTicks = beat >= BurstSequence.CUTS ? 24 : beat == BurstSequence.CUTS-1 ? 12 : 8;
        beginSweep(player, hand, hand == InteractionHand.MAIN_HAND ? 0.52f : 0.36f,
            beat >= BurstSequence.CUTS, holdTicks, beat, true, beat<BurstSequence.CUTS ? BurstSequence.CUT_DAMAGE_SCALE : 1f);
    }

    static void followUpCue(ServerPlayer player) {
        CombatPackets.stroke(player, InteractionHand.OFF_HAND, BladeMotion.FOLLOW_UP, 0);
    }

    static void offhandSweep(ServerPlayer player) {
        beginSweep(player, InteractionHand.OFF_HAND, 0.65f, true, 0, 0, false,1f);
    }

    static void fallback(ServerPlayer player, InteractionHand hand) {
        beginSweep(player, hand, 0.24f, false, 8, 0, true,1f);
    }

    private static void beginSweep(ServerPlayer player, InteractionHand hand, float ratio, boolean wide,
            int holdTicks, int beat, boolean burst,float damageScale) {
        var level = player.level();
        var main = player.getMainHandItem();
        var off = player.getOffhandItem();
        player.swing(hand, true);
        CombatPackets.stroke(player, hand, BladeMotion.BURST, beat);
        StrokeScheduler.after(player, level::getGameTime, BladeMotion.contactTicks(BladeMotion.BURST),
            () -> level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.isAlive() && !player.isSpectator() && player.level() == level
                && main == player.getMainHandItem() && off == player.getOffhandItem()
                && BladeTypes.hasUsablePair(player) && (!burst || DualWieldCombat.active(player)),
            () -> sweep(player, hand, LONG_REACH, ratio, wide, holdTicks,damageScale));
    }

    private static void sweep(ServerPlayer player, InteractionHand hand, double reach, float ratio, boolean wide,
            int holdTicks,float damageScale) {
        var stack = player.getItemInHand(hand);
        var state = CapabilitySlashBlade.getBladeState(stack).orElseThrow();
        var blade = (ItemSlashBlade) stack.getItem();
        double weaponDamage = blade.getDefaultAttributeModifiers(stack).modifiers().stream()
            .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
            .mapToDouble(entry -> entry.modifier().amount()).sum();
        float damage = BurstSequence.damage(weaponDamage + 1 + AttackHelper.getRankBonus(player),ratio,damageScale);
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
                    impact(player, candidate.physical());
                    if (holdTicks > 0) BurstTargetHold.hold(target, holdTicks);
                }
            }
            return any;
        });
        if (hand == InteractionHand.MAIN_HAND) {
            if (hit) stack.hurtAndBreak(1, level, player, ItemSlashBlade.getOnBroken(stack, player));
        }
        swingSound(player, hand);
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

    static void swingSound(ServerPlayer player, InteractionHand hand) {
        player.level().playSound(null, player, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS,
            0.52f, hand == InteractionHand.MAIN_HAND ? 0.95f : 1.25f);
    }

    /** Confirm only successful damage, on the struck body/part surface nearest the blade owner. */
    static void impact(ServerPlayer player, Entity physical) {
        if (physical == null) return;
        var bounds = physical.getBoundingBox();
        var origin = player.position().add(0, player.getEyeHeight() * 0.72, 0);
        var point = new Vec3(Mth.clamp(origin.x, bounds.minX, bounds.maxX),
            Mth.clamp(origin.y, bounds.minY, bounds.maxY), Mth.clamp(origin.z, bounds.minZ, bounds.maxZ));
        point = point.add(origin.subtract(point).normalize().scale(0.025));
        var level = player.level();
        level.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 6, 0.12, 0.12, 0.12, 0.035);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, point.x, point.y, point.z, 3, 0.05, 0.05, 0.05, 0.01);
        level.playSound(null, point.x, point.y, point.z, SoundEvents.PLAYER_ATTACK_STRONG,
            SoundSource.PLAYERS, 0.55f, 1.2f);
    }
}
