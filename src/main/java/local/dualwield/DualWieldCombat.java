package local.dualwield;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import local.dualwield.arts.HandSlashArts;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

public final class DualWieldCombat {
    private static final int GUARD_TICKS = 20;
    private static final int INVULNERABILITY_TICKS = 16;
    private static final int COOLDOWN_TICKS = 160;
    private static final int SWEEP_COOLDOWN_TICKS = 40;
    private static final int[] TIMING = {0, 6, 12, 18, 24, 30, 36, 42, 52, 56, 60, 64, 68};
    private static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(DualWield.ID, "burst_slow");
    private static final AttributeModifier BURST_SLOW = new AttributeModifier(SLOW_ID, -0.35,
        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    private static final Map<UUID, State> STATES = new HashMap<>();

    private DualWieldCombat() {}

    static void register() {
        CombatPackets.register();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((target, source, baseDamage, taken, blocked) -> {
            if (target instanceof ServerPlayer player && taken > 0 && !blocked) finishGuard(player, source);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            BurstTargetHold.tick();
            FollowUp.tick();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) tick(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var state = STATES.remove(handler.player.getUUID());
            if (state != null) clearActions(handler.player, state);
        });
    }

    public static boolean active(LivingEntity player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return false;
        var state = STATES.get(player.getUUID());
        return state != null && state.burst && state.valid(serverPlayer);
    }

    public static void equipmentChanged(ServerPlayer player) {
        var state = STATES.get(player.getUUID());
        if (state == null) return;
        clearActions(player, state);
        status(player, state);
    }

    public static float clampYaw(float yaw, float startingYaw) {
        return startingYaw + Mth.clamp(Mth.wrapDegrees(yaw - startingYaw), -60f, 60f);
    }

    public static double burstArtReach(LivingEntity player, double normalReach) {
        return active(player) || HandSlashArts.extendsMeleeReach(player) ? Math.max(BladeSweep.LONG_REACH, normalReach)
            : normalReach;
    }

    public static boolean guarding(LivingEntity target) {
        if (!(target instanceof ServerPlayer player)) return false;
        var state = STATES.get(player.getUUID());
        return state != null && state.guardUntil > player.level().getGameTime() && state.valid(player);
    }

    private static boolean guardable(ServerPlayer defender, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FIRE)
                || source.is(DamageTypes.THORNS) || source.getEntity() == defender) return false;
        var direct = source.getDirectEntity();
        return direct instanceof LivingEntity || direct instanceof Projectile || source.is(DamageTypeTags.IS_PROJECTILE);
    }

    public static float guardDamage(LivingEntity target, DamageSource source, float damage) {
        if (target instanceof ServerPlayer player && guarding(player) && guardable(player, source)) return damage * 0.2f;
        return damage;
    }

    public static boolean counterInvulnerable(LivingEntity target, DamageSource source) {
        if (!(target instanceof ServerPlayer player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        var state = STATES.get(player.getUUID());
        return state != null && state.owner == player && state.level == player.level()
            && player.isAlive() && state.invulnerableUntil > player.level().getGameTime();
    }

    private static void finishGuard(ServerPlayer player, DamageSource source) {
        if (!guarding(player) || !guardable(player, source)) return;
        var state = STATES.get(player.getUUID());
        state.guardUntil = -1;
        state.invulnerableUntil = player.level().getGameTime() + INVULNERABILITY_TICKS;
        state.counterArtUntil = state.invulnerableUntil;
        BladeSweep.counter(player);
    }

    public static void press(ServerPlayer player, int action) {
        if (action != CombatPackets.ACTION || !player.isAlive() || player.isSpectator() || active(player)) return;
        long now = player.level().getGameTime();
        var state = STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        if (!state.valid(player)) clearActions(player, state);
        if (!BladeTypes.hasUsablePair(player)) return;
        if (BladeTypes.isWakizashi(player.getOffhandItem())) {
            if (state.counterArtUntil > now) {
                state.counterArtUntil = 0;
                player.stopUsingItem();
                HandSlashArts.cast(player, InteractionHand.OFF_HAND, 1f, false);
                return;
            }
            if (state.guardUntil > now || state.invulnerableUntil > now) return;
            state.capture(player);
            state.guardUntil = now + GUARD_TICKS;
            player.stopUsingItem();
            CombatPackets.animateOffhand(player, CombatPackets.GUARD);
            return;
        }
        if (BladeTypes.isWakizashi(player.getMainHandItem())) {
            if (state.sweepCooldownUntil > now) return;
            state.sweepCooldownUntil = now + SWEEP_COOLDOWN_TICKS;
            player.stopUsingItem();
            BladeSweep.offhandSweep(player);
            return;
        }
        if (state.cooldownUntil > now) return;
        state.capture(player);
        state.burst = true;
        state.start = now;
        state.next = 0;
        state.yaw = player.getYRot();
        state.cooldownUntil = now + COOLDOWN_TICKS;
        player.stopUsingItem();
        player.getAttribute(Attributes.MOVEMENT_SPEED).addTransientModifier(BURST_SLOW);
        status(player, state);
        runBurst(player, state, now);
    }

    private static void tick(ServerPlayer player) {
        var state = STATES.get(player.getUUID());
        if (state == null) return;
        long now = player.level().getGameTime();
        if ((state.burst || state.guardUntil >= 0 || state.counterArtUntil > 0) && !state.valid(player)) {
            clearActions(player, state);
            status(player, state);
        }
        if (state.burst) runBurst(player, state, now);
        if (state.guardUntil >= 0 && now >= state.guardUntil) {
            state.guardUntil = -1;
            CombatPackets.animateOffhand(player, CombatPackets.LOWER_GUARD);
        }
        if (state.counterArtUntil > 0 && now >= state.counterArtUntil) state.counterArtUntil = 0;
        if (!state.burst && state.guardUntil < 0 && now >= state.invulnerableUntil
                && now >= state.cooldownUntil && now >= state.sweepCooldownUntil) {
            STATES.remove(player.getUUID());
        }
    }

    private static void runBurst(ServerPlayer player, State state, long now) {
        player.setYRot(clampYaw(player.getYRot(), state.yaw));
        if (state.next >= TIMING.length || now - state.start < TIMING[state.next]) return;
        int beat = state.next++;
        if (beat < 8) {
            BladeSweep.burst(player, hand(beat), beat);
        } else if (beat == 8) {
            BladeSweep.burst(player, InteractionHand.MAIN_HAND, beat);
            if (state.valid(player)) BladeSweep.burst(player, InteractionHand.OFF_HAND, beat + 1);
        } else {
            var hand = hand(beat - 9);
            boolean secondary = hand == InteractionHand.OFF_HAND;
            if (!HandSlashArts.cast(player, hand, secondary ? 0.45f : 0.65f, secondary)) {
                BladeSweep.fallback(player, hand);
            }
        }
        if (state.next == TIMING.length || !state.valid(player)) {
            clearActions(player, state);
            status(player, state);
        }
    }

    private static InteractionHand hand(int beat) {
        return beat % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private static void clearActions(ServerPlayer player, State state) {
        if (state.burst) player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SLOW_ID);
        if (state.guardUntil >= 0) CombatPackets.animateOffhand(player, CombatPackets.LOWER_GUARD);
        state.burst = false;
        state.guardUntil = -1;
        state.counterArtUntil = 0;
    }

    private static void status(ServerPlayer player, State state) {
        CombatPackets.status(player, state.burst, state.yaw);
    }

    private static final class State {
        ServerPlayer owner;
        ServerLevel level;
        ItemStack main, off;
        long guardUntil = -1, invulnerableUntil, counterArtUntil, cooldownUntil, sweepCooldownUntil, start;
        int next;
        float yaw;
        boolean burst;

        void capture(ServerPlayer player) {
            owner = player;
            level = player.level();
            main = player.getItemBySlot(EquipmentSlot.MAINHAND);
            off = player.getItemBySlot(EquipmentSlot.OFFHAND);
        }

        boolean valid(ServerPlayer player) {
            return owner == player && player.isAlive() && !player.isSpectator() && level == player.level()
                && main == player.getItemBySlot(EquipmentSlot.MAINHAND) && off == player.getItemBySlot(EquipmentSlot.OFFHAND)
                && BladeTypes.isUsable(main) && BladeTypes.isUsable(off);
        }

    }
}
