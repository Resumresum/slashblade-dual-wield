package local.dualwield;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import mods.flammpfeil.slashblade.SlashBladeConfig;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackHelper;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class FollowUp {
    private static final Map<ServerPlayer, Action> CURRENT = new WeakHashMap<>();
    private static final Map<EntitySlashEffect, Action> SLASHES = new WeakHashMap<>();
    private static final ArrayList<Impact> QUEUE = new ArrayList<>();
    private static final ThreadLocal<Sweep> SWEEP = new ThreadLocal<>();
    private static final ThreadLocal<QuietImpact> WITHOUT_KNOCKBACK = new ThreadLocal<>();
    private static final float DAMAGE_RATIO = 0.75f;
    private static final int DELAY_TICKS = 4;

    private FollowUp() {}

    public static void clear(ServerPlayer player) {
        CURRENT.remove(player);
        SLASHES.values().removeIf(action -> action.pair != null && action.pair.isOwnedBy(player));
        QUEUE.removeIf(impact -> impact.player == player);
    }

    public static void markSlash(LivingEntity attacker, EntitySlashEffect slash) {
        if (slash == null || !(attacker instanceof ServerPlayer player)) return;
        var action = action(player);
        if (action != null) SLASHES.put(slash, action);
    }

    public static <T> T withSlash(EntitySlashEffect slash, Supplier<T> attacks) {
        if (slash.level().isClientSide()) return attacks.get();
        var previous = SWEEP.get();
        SWEEP.set(new Sweep(SLASHES.get(slash)));
        try {
            return attacks.get();
        } finally {
            if (previous == null) SWEEP.remove();
            else SWEEP.set(previous);
        }
    }

    public static float extraDamage(LivingEntity attacker, Entity target, float comboRatio) {
        if (!(attacker instanceof ServerPlayer player) || !(target instanceof LivingEntity living)
                || findAction(player) == null || DualWieldCombat.active(player)) return 0;
        double base = player.getAttributeValue(Attributes.ATTACK_DAMAGE)
            + AttackHelper.getSweepingBonus(player) + AttackHelper.getRankBonus(player);
        base *= comboRatio * AttackManager.getSlashBladeDamageScale(player)
            * SlashBladeConfig.SLASHBLADE_DAMAGE_MULTIPLIER.get();
        if (AttackHelper.isCriticalHit(player, living)) base *= 1.5;
        return (float) Math.max(0, base * DAMAGE_RATIO);
    }

    public static void hit(LivingEntity attacker, Entity target, float extraDamage) {
        if (!(attacker instanceof ServerPlayer player) || !(target instanceof LivingEntity living)
                || !living.isAlive() || extraDamage <= 0) return;
        var action = findAction(player);
        if (action == null || !action.matches(player) || DualWieldCombat.active(player)
                || !action.targets.add(living.getUUID())) return;
        QUEUE.add(new Impact(player, living, CombatTargeting.physical(player, living), action,
            player.level().getGameTime() + DELAY_TICKS, extraDamage));
    }

    private static Action findAction(ServerPlayer player) {
        var sweep = SWEEP.get();
        return sweep == null ? action(player) : sweep.action;
    }

    private static Action action(ServerPlayer player) {
        if (DualWieldCombat.active(player) || !BladeTypes.hasPair(player, false)) return null;
        var blade = player.getMainHandItem();
        var state = CapabilitySlashBlade.getBladeState(blade).orElseThrow();
        Identifier combo = state.getComboSeq();
        if (!ordinary(combo)) return null;
        long start = state.getLastActionTime();
        var previous = CURRENT.get(player);
        if (previous != null && previous.combo.equals(combo) && previous.start == start && previous.matches(player)) {
            return previous;
        }
        var action = new Action(player.level(), blade, player.getOffhandItem(), combo, start, new HashSet<>(),
            CombatGrowth.capture(player));
        CURRENT.put(player, action);
        return action;
    }

    private static boolean ordinary(Identifier combo) {
        if (combo == null || !combo.getNamespace().equals("slashblade")) return false;
        String path = combo.getPath();
        return path.startsWith("combo_a") || path.startsWith("combo_b") || path.equals("combo_c")
            || path.startsWith("aerial_rave_") || path.startsWith("aerial_cleave")
            || path.equals("upperslash") || path.equals("upperslash_jump")
            || path.equals("rapid_slash") || path.equals("rising_star");
    }

    static void tick() {
        if (QUEUE.isEmpty()) return;
        var ready = new ArrayList<Impact>();
        QUEUE.removeIf(impact -> {
            if (impact.impactTick > impact.action.level.getGameTime()) return false;
            ready.add(impact);
            return true;
        });
        var animated = new HashSet<ServerPlayer>();
        for (var impact : ready) {
            if (impact.target.isAlive() && impact.target.level() == impact.player.level()
                    && impact.action.matches(impact.player) && !DualWieldCombat.active(impact.player)
                    && (!(impact.target instanceof ServerPlayer other) || impact.player.canHarmPlayer(other))) {
                if (CombatGrowth.withOffhand(impact.player,
                        () -> CombatTargeting.followUp(impact.player, impact.target, impact.physical,
                            () -> damageWithoutKnockback(impact.player, impact.target, impact.damage)))
                        && impact.action.matches(impact.player) && animated.add(impact.player)) {
                    BladeSweep.followUpCue(impact.player);
                }
            }
        }
    }

    public static boolean suppressesKnockback(LivingEntity target) {
        var impact = WITHOUT_KNOCKBACK.get();
        return impact != null && impact.target == target;
    }

    public static boolean suppressesRank(LivingEntity target) {
        var impact = WITHOUT_KNOCKBACK.get();
        return impact != null && impact.target == target && !impact.creditRank;
    }

    static boolean damageWithoutKnockback(ServerPlayer player, LivingEntity target, float damage) {
        return damageWithoutKnockback(player, target, damage, false);
    }

    static boolean damageWithoutKnockback(ServerPlayer player, LivingEntity target, float damage, boolean creditRank) {
        int previousImmunity = target.invulnerableTime;
        var previousImpact = WITHOUT_KNOCKBACK.get();
        WITHOUT_KNOCKBACK.set(new QuietImpact(target, creditRank));
        try {
            target.invulnerableTime = 0;
            return CombatTargeting.damage(target,
                () -> target.hurtServer(player.level(), player.damageSources().playerAttack(player), damage));
        } finally {
            target.invulnerableTime = previousImmunity;
            if (previousImpact == null) WITHOUT_KNOCKBACK.remove();
            else WITHOUT_KNOCKBACK.set(previousImpact);
        }
    }

    private record Action(ServerLevel level, ItemStack main, ItemStack off, Identifier combo, long start,
            Set<UUID> targets, CombatGrowth.Pair pair) {
        boolean matches(ServerPlayer player) {
            return pair != null && pair.valid() && player.isAlive() && level == player.level() && main == player.getMainHandItem()
                && off == player.getOffhandItem() && BladeTypes.hasPair(player, false);
        }
    }

    private record Sweep(Action action) {}
    private record QuietImpact(LivingEntity target, boolean creditRank) {}
    private record Impact(ServerPlayer player, LivingEntity target, Entity physical, Action action,
            long impactTick, float damage) {}
}
