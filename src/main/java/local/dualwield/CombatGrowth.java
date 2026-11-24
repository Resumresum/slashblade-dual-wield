package local.dualwield;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Shares only native combat rewards for targets actually damaged by the equipped offhand. */
public final class CombatGrowth {
    private static final String KILLS = "slashblade_dual_wield.killTenths";
    private static final String SOULS = "slashblade_dual_wield.soulTenths";
    private static final Map<LivingEntity, Map<UUID, Pair>> FIGHTS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> EQUIPMENT = new WeakHashMap<>();
    private static final ThreadLocal<Pair> OFFHAND = new ThreadLocal<>();
    private static final ThreadLocal<ArrayDeque<Reward>> REWARDS = ThreadLocal.withInitial(ArrayDeque::new);

    private CombatGrowth() {}

    public static void register() {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> clear(player));
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) clear(player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clear(handler.player);
            EQUIPMENT.remove(handler.player);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> FIGHTS.values().removeIf(fight -> {
            fight.values().removeIf(pair -> !pair.valid());
            return fight.isEmpty();
        }));
    }

    public static Pair capture(ServerPlayer player) {
        return BladeTypes.hasUsablePair(player) ? new Pair(player) : null;
    }

    public static void equipmentChanged(ServerPlayer player) {
        EQUIPMENT.merge(player, 1, Integer::sum);
    }

    private static void clear(ServerPlayer player) {
        equipmentChanged(player);
        FIGHTS.values().removeIf(fight -> {
            fight.values().removeIf(pair -> pair.isOwnedBy(player));
            return fight.isEmpty();
        });
        HandSlashArts.clear(player);
        FollowUp.clear(player);
        DualWieldCombat.equipmentChanged(player);
    }

    public static <T> T withOffhand(ServerPlayer player, Supplier<T> damage) {
        Pair previous = OFFHAND.get();
        Pair pair = capture(player);
        if (pair == null) OFFHAND.remove();
        else OFFHAND.set(pair);
        try { return damage.get(); }
        finally {
            if (previous == null) OFFHAND.remove();
            else OFFHAND.set(previous);
        }
    }

    /** Called after damage mitigation, before a potentially lethal health change. */
    public static void damaged(LivingEntity target, DamageSource source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return;
        Pair pair = OFFHAND.get();
        if (pair == null) pair = HandSlashArts.participatingOffhand(player);
        if (pair != null && pair.player == player && pair.valid() && target.level() == pair.level) {
            FIGHTS.computeIfAbsent(target, ignored -> new HashMap<>()).put(player.getUUID(), pair);
        }
    }

    public static void beginReward(LivingEntity target, LivingEntity attacker) {
        var fight = FIGHTS.get(target);
        Pair pair = attacker == null || fight == null ? null : fight.get(attacker.getUUID());
        REWARDS.get().push(new Reward(pair != null && pair.valid() && target.level() == pair.level ? pair : null));
    }

    public static void endReward() {
        var rewards = REWARDS.get();
        rewards.pop();
        if (rewards.isEmpty()) REWARDS.remove();
    }

    public static void reward(ItemStack blade, ISlashBladeState state, int total, boolean souls) {
        int before = souls ? state.getProudSoulCount() : state.getKillCount();
        int amount = total - before;
        var rewards = REWARDS.get();
        Pair pair = rewards.isEmpty() ? null : rewards.peek().pair;
        if (amount <= 0 || pair == null || !pair.valid()
                || pair.main != blade) {
            set(state, total, souls);
            return;
        }
        add(pair.main, amount, 7, souls);
        add(pair.off, amount, 3, souls);
    }

    public static int remainder(ItemStack blade, boolean souls) {
        var data = blade.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : Math.floorMod(data.copyTag().getIntOr(souls ? SOULS : KILLS, 0), 10);
    }

    private static void add(ItemStack blade, int amount, int share, boolean souls) {
        long tenths = (long) amount * share + remainder(blade, souls);
        var state = CapabilitySlashBlade.getBladeState(blade).orElseThrow();
        set(state, (souls ? state.getProudSoulCount() : state.getKillCount()) + (int) (tenths / 10), souls);
        CustomData.update(DataComponents.CUSTOM_DATA, blade,
            tag -> tag.putInt(souls ? SOULS : KILLS, (int) (tenths % 10)));
    }

    private static void set(ISlashBladeState state, int total, boolean souls) {
        if (souls) state.setProudSoulCount(total);
        else state.setKillCount(total);
    }

    public static final class Pair {
        private final ServerPlayer player;
        private final ServerLevel level;
        private final ItemStack main, off;
        private final int equipment;

        private Pair(ServerPlayer player) {
            this.player = player;
            level = player.level();
            main = player.getItemBySlot(EquipmentSlot.MAINHAND);
            off = player.getItemBySlot(EquipmentSlot.OFFHAND);
            equipment = EQUIPMENT.getOrDefault(player, 0);
        }

        public boolean valid() {
            return level.getServer().getPlayerList().getPlayer(player.getUUID()) == player
                && player.isAlive() && player.level() == level && equipment == EQUIPMENT.getOrDefault(player, 0)
                && main == player.getItemBySlot(EquipmentSlot.MAINHAND)
                && off == player.getItemBySlot(EquipmentSlot.OFFHAND)
                && BladeTypes.isBlade(main) && BladeTypes.isBlade(off);
        }

        public boolean isOwnedBy(ServerPlayer owner) { return player == owner; }
    }

    private record Reward(Pair pair) {}
}
