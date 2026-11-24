package local.dualwield;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** Uses the native target/managed-attack path, including the optional multipart compatibility patch. */
final class CombatTargeting {
    private static final Method AREA, HIT_PART;
    static {
        Method area = null, part = null;
        try {
            Class<?> targets = Class.forName("local.sbcombat.CombatTargets");
            area = targets.getMethod("area", Supplier.class);
            for (Method method : targets.getMethods()) {
                if (method.getName().equals("hitPart") && method.getParameterCount() == 2) part = method;
            }
            if (part == null) throw new NoSuchMethodException("CombatTargets.hitPart");
        } catch (ClassNotFoundException absent) {
            // The dual-wield add-on also supports installations without the optional patch.
        } catch (ReflectiveOperationException broken) {
            throw new IllegalStateException("Unsupported SlashBlade combat compatibility patch", broken);
        }
        AREA = area;
        HIT_PART = part;
    }

    private CombatTargeting() {}

    static <T> T area(ServerPlayer player, double reach, Function<List<Hit>, T> action) {
        Supplier<T> scan = () -> {
            List<Hit> hits = new ArrayList<>();
            Set<LivingEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Entity entity : TargetSelector.getTargettableEntitiesWithinAABB(player.level(), player,
                    player.getBoundingBox().inflate(reach), reach)) {
                if (!(entity instanceof LivingEntity target) || target == player || !target.isAlive()
                        || !target.isAttackable() || target.skipAttackInteraction(player) || !seen.add(target)
                        || target instanceof Player other && !player.canHarmPlayer(other)) continue;
                Entity physical = physical(player, target);
                if (physical != null && player.hasLineOfSight(physical)) hits.add(new Hit(target, physical));
            }
            return action.apply(hits);
        };
        return AREA == null ? scan.get() : invoke(AREA, scan);
    }

    static Entity physical(ServerPlayer player, LivingEntity target) {
        if (HIT_PART == null || !HIT_PART.getParameterTypes()[0].isInstance(target)) return target;
        Entity[] part = {null};
        AttackManager.doManagedAttack(ignored -> part[0] = invoke(HIT_PART, target,
            player.damageSources().playerAttack(player)), target, false, false);
        return part[0];
    }

    static boolean damage(LivingEntity target, Supplier<Boolean> damage) {
        boolean[] hit = {false};
        AttackManager.doManagedAttack(ignored -> hit[0] = damage.get(), target, false, false);
        return hit[0];
    }

    static boolean followUp(ServerPlayer player, LivingEntity target, Entity originalPart, Supplier<Boolean> damage) {
        if (originalPart == target) return damage.get();
        if (originalPart == null) return false;
        double reach = TargetSelector.getResolvedReach(player);
        return area(player, reach, hits -> hits.stream().anyMatch(hit -> hit.target == target
            && hit.physical == originalPart && damage.get()));
    }

    @SuppressWarnings("unchecked")
    private static <T> T invoke(Method method, Object... arguments) {
        try { return (T) method.invoke(null, arguments); }
        catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Combat compatibility callback failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Combat compatibility callback inaccessible", failure);
        }
    }

    record Hit(LivingEntity target, Entity physical) {}
}
