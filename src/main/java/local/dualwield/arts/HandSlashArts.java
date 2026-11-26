package local.dualwield.arts;

import java.util.Map;
import java.util.WeakHashMap;
import local.dualwield.CombatPackets;
import local.dualwield.CombatGrowth;
import local.dualwield.DualWieldCombat;
import cn.sh1rocu.slashblade.api.extension.EntityExtension;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import mods.flammpfeil.slashblade.registry.SlashArtsRegistry;
import mods.flammpfeil.slashblade.slasharts.SlashArts;
import mods.flammpfeil.slashblade.slasharts.JudgementCut;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.resources.Identifier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

/** Runs a Slash Art against the actual blade in its equipped hand. */
public final class HandSlashArts {
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<MarkerBackup> OFFHAND_BACKUP = new ThreadLocal<>();
    private static final Map<ServerPlayer, Pending> MAIN_TIMELINES = new WeakHashMap<>();
    private static final Map<ServerPlayer, Pending> OFFHAND_TIMELINES = new WeakHashMap<>();
    private static final Map<Object, Context> DELAYED = new WeakHashMap<>();
    private static final Identifier NONE = ComboStateRegistry.getId(ComboStateRegistry.NONE);
    private static final String OFFHAND_TICK_KEY = "slashblade_dual_wield.offhandLastProcessedTick";

    private HandSlashArts() {}

    public static void clear(ServerPlayer player) {
        MAIN_TIMELINES.remove(player);
        OFFHAND_TIMELINES.remove(player);
        DELAYED.values().removeIf(context -> context.player == player);
    }

    public static boolean canCast(ServerPlayer player, InteractionHand hand) {
        if (player == null || hand == null || player.level().isClientSide()) return false;
        ItemStack blade = player.getItemInHand(hand);
        if (!(blade.getItem() instanceof ItemSlashBlade) || !SwordType.from(blade).contains(SwordType.ENCHANTED)) return false;
        var optional = CapabilitySlashBlade.getBladeState(blade);
        if (optional.isEmpty()) return false;
        ISlashBladeState state = optional.get();
        return !state.isBroken() && !state.isSealed()
            && !SlashArtsRegistry.SLASH_ARTS.getKey(SlashArtsRegistry.NONE).equals(state.getSlashArtsKey());
    }

    /** Returns true only when an art was dispatched. Payment is applied exactly once. */
    public static boolean cast(ServerPlayer player, InteractionHand hand, float damageMultiplier, boolean free) {
        if (!canCast(player, hand) || !Float.isFinite(damageMultiplier) || damageMultiplier <= 0 || ACTIVE.get() != null) return false;
        ItemStack blade = player.getItemInHand(hand);
        ISlashBladeState state = CapabilitySlashBlade.getBladeState(blade).orElseThrow();
        SlashArts art = state.getSlashArts();
        Identifier artKey = SlashArtsRegistry.SLASH_ARTS.getKey(art);
        if (artKey == null) return false;
        boolean tsuki = artKey.getNamespace().equals("twilighttsuki");
        int cost = tsuki ? tsukiCost(artKey.getPath()) : art.getProudSoulCost();
        int fallback = tsuki ? tsukiFallback(artKey.getPath()) : 1;
        if (cost < 0) return false; // An unknown external art may have its own payment/effects.

        boolean burst = DualWieldCombat.active(player);
        activate(new Context(player, hand, blade, damageMultiplier, burst));
        try {
            Identifier combo = art.doArts(SlashArts.ArtsType.Success, player);
            if (combo == null) return false;
            boolean immediate = tsuki && (artKey.getPath().equals("rainbowstrike")
                || artKey.getPath().equals("rainbowdrive") || artKey.getPath().equals("tfproudshield"));
            if (!immediate && (combo.equals(NONE) || ComboStateRegistry.COMBO_STATE.getValue(combo) == null)) return false;
            if (artKey.getNamespace().equals("slashblade")) {
                // These native windups outlast the interval before the next cast on the same blade.
                combo = switch (artKey.getPath()) {
                    case "judgement_cut" -> {
                        var cut = JudgementCut.doJudgementCut(player);
                        cut.setDamage(cut.getDamage() * damageMultiplier);
                        captureDelayed(cut);
                        yield ComboStateRegistry.getId(player.onGround() ? ComboStateRegistry.JUDGEMENT_CUT_SHEATH
                            : ComboStateRegistry.JUDGEMENT_CUT_SHEATH_AIR);
                    }
                    case "void_slash" -> {
                        AttackManager.doVoidSlashAttack(player);
                        yield ComboStateRegistry.getId(ComboStateRegistry.VOID_SLASH_SHEATH);
                    }
                    case "piercing" -> combo.equals(ComboStateRegistry.getId(ComboStateRegistry.PIERCING))
                        ? ComboStateRegistry.getId(ComboStateRegistry.PIERCING_2) : combo;
                    default -> combo;
                };
            }
            if (!immediate) state.updateComboSeq(player, combo);
            if (!free && !player.getAbilities().instabuild) pay(player, blade, state, cost, fallback);
            if (!immediate) timelines(hand).put(player, new Pending(blade, damageMultiplier, burst, ACTIVE.get().pair));
            player.swing(hand, true);
            if (hand == InteractionHand.OFF_HAND) CombatPackets.swingOffhand(player);
            else CombatPackets.mainArt(player);
            return true;
        } finally {
            deactivate();
        }
    }

    private static void pay(ServerPlayer player, ItemStack blade, ISlashBladeState state, int cost, int fallback) {
        if (state.getProudSoulCount() >= cost) state.setProudSoulCount(state.getProudSoulCount() - cost);
        else blade.hurtAndBreak(fallback, player.level(), player, ItemSlashBlade.getOnBroken(blade, player));
    }

    public static ItemStack routedMainHand(net.minecraft.world.entity.LivingEntity entity) {
        Context context = ACTIVE.get();
        return context != null && context.player == entity && context.hand == InteractionHand.OFF_HAND ? context.blade : null;
    }

    public static CombatGrowth.Pair participatingOffhand(ServerPlayer player) {
        Context context = ACTIVE.get();
        return context != null && context.player == player && context.hand == InteractionHand.OFF_HAND
            ? context.pair : null;
    }

    public static float damageMultiplier() {
        Context context = ACTIVE.get();
        return context == null ? 1.0f : context.damageMultiplier;
    }

    public static double attackAttribute(LivingEntity entity, double original) {
        var context = ACTIVE.get();
        return context != null && context.player == entity ? context.attackAttribute : original;
    }

    public static boolean extendsMeleeReach(LivingEntity entity) {
        var context = ACTIVE.get();
        return context != null && context.player == entity && context.burst;
    }

    public static float voidImpactMultiplier() {
        var context = ACTIVE.get();
        return context != null && context.voidSlash ? context.damageMultiplier : 1f;
    }

    public static boolean suppressInternalPayment() { return ACTIVE.get() != null; }

    public static void captureDelayed(Object entity) {
        Context context = ACTIVE.get();
        if (context != null) DELAYED.put(entity, context);
    }

    public static void beginDelayed(Object entity) {
        Context context = DELAYED.get(entity);
        if (context != null && ACTIVE.get() == null) activate(context);
    }

    public static void endDelayed(Object entity) {
        Context context = DELAYED.get(entity);
        if (context != null && ACTIVE.get() == context) deactivate();
    }

    /** Called for the equipped blade's inventory tick. */
    public static boolean tickTimeline(ServerPlayer player, InteractionHand hand, ItemStack blade) {
        Map<ServerPlayer, Pending> timelines = timelines(hand);
        Pending pending = timelines.get(player);
        if (pending == null || pending.blade != blade || player.getItemInHand(hand) != blade) {
            if (pending != null) timelines.remove(player);
            return false;
        }
        var optional = CapabilitySlashBlade.getBladeState(blade);
        if (optional.isEmpty()) { timelines.remove(player); return false; }
        ISlashBladeState state = optional.get();
        if (ACTIVE.get() != null) return false;
        activate(new Context(player, hand, blade, pending.damageMultiplier, pending.burst, pending.pair));
        try {
            Identifier location = state.resolveCurrentComboState(player);
            var combo = ComboStateRegistry.COMBO_STATE.getValue(location);
            if (combo == null || location.equals(NONE) || location.equals(state.getComboRoot())) {
                timelines.remove(player);
                return false;
            }
            combo.tickAction(player);
            return true;
        } finally {
            deactivate();
        }
    }

    private static Map<ServerPlayer, Pending> timelines(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? MAIN_TIMELINES : OFFHAND_TIMELINES;
    }

    private static void activate(Context context) {
        if (ACTIVE.get() != null) throw new IllegalStateException("Nested Slash Art context");
        if (context.hand == InteractionHand.OFF_HAND) {
            CompoundTag data = ((EntityExtension) context.player).sb$getPersistentData();
            String common = ComboState.LAST_PROCESSED_TICK_KEY;
            OFFHAND_BACKUP.set(new MarkerBackup(data.contains(common), data.getIntOr(common, 0)));
            if (data.contains(OFFHAND_TICK_KEY)) data.putInt(common, data.getIntOr(OFFHAND_TICK_KEY, 0));
            else data.remove(common);
        }
        ACTIVE.set(context);
    }

    private static void deactivate() {
        Context context = ACTIVE.get();
        if (context != null && context.hand == InteractionHand.OFF_HAND) {
            CompoundTag data = ((EntityExtension) context.player).sb$getPersistentData();
            String common = ComboState.LAST_PROCESSED_TICK_KEY;
            if (data.contains(common)) data.putInt(OFFHAND_TICK_KEY, data.getIntOr(common, 0));
            else data.remove(OFFHAND_TICK_KEY);
            MarkerBackup backup = OFFHAND_BACKUP.get();
            if (backup != null && backup.present) data.putInt(common, backup.value);
            else data.remove(common);
            OFFHAND_BACKUP.remove();
        }
        ACTIVE.remove();
    }

    private static int tsukiCost(String path) {
        return switch (path) {
            case "crescentslash", "tffrostdrive", "tffierydrive" -> 10;
            case "tsukiend", "legacy_maximum_bet" -> 20;
            case "tsukiwaveedge" -> 23;
            case "randomblink" -> 6;
            case "fulllunar", "legacy_wither_swords" -> 40;
            case "firstaid" -> 16;
            case "rainbowstrike" -> 22;
            case "rainbowdrive" -> 21;
            case "tfproudshield" -> 30;
            case "tftripledrive" -> 15;
            default -> -1;
        };
    }

    private static int tsukiFallback(String path) {
        return switch (path) {
            case "crescentslash", "randomblink", "firstaid", "rainbowstrike", "rainbowdrive",
                 "tffrostdrive", "tffierydrive", "tfproudshield", "tftripledrive" -> 5;
            default -> 10;
        };
    }

    private static double bladeAttackModifier(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSlashBlade blade)) return 0;
        return blade.getDefaultAttributeModifiers(stack).modifiers().stream()
            .filter(entry -> entry.attribute().equals(Attributes.ATTACK_DAMAGE))
            .mapToDouble(entry -> entry.modifier().amount()).sum();
    }

    private record Context(ServerPlayer player, InteractionHand hand, ItemStack blade, float damageMultiplier,
            boolean burst, double attackAttribute, boolean voidSlash, CombatGrowth.Pair pair) {
        Context(ServerPlayer player, InteractionHand hand, ItemStack blade, float damageMultiplier, boolean burst) {
            this(player, hand, blade, damageMultiplier, burst, CombatGrowth.capture(player));
        }

        Context(ServerPlayer player, InteractionHand hand, ItemStack blade, float damageMultiplier, boolean burst,
                CombatGrowth.Pair pair) {
            this(player, hand, blade, damageMultiplier, burst, player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                + (hand == InteractionHand.OFF_HAND ? bladeAttackModifier(blade)
                    - bladeAttackModifier(player.getItemBySlot(EquipmentSlot.MAINHAND)) : 0),
                CapabilitySlashBlade.getBladeState(blade)
                    .filter(state -> Identifier.fromNamespaceAndPath("slashblade", "void_slash").equals(state.getSlashArtsKey()))
                    .isPresent(), pair);
        }
    }
    private record Pending(ItemStack blade, float damageMultiplier, boolean burst, CombatGrowth.Pair pair) {}
    private record MarkerBackup(boolean present, int value) {}
}
