package local.dualwield;

import java.util.Map;
import java.util.WeakHashMap;
import mods.flammpfeil.slashblade.ability.StunManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

public final class BurstTargetHold {
    private static final Map<Mob, Long> HELD = new WeakHashMap<>();

    private BurstTargetHold() {}

    static void hold(LivingEntity target, int ticks) {
        if (!(target instanceof Mob mob) || !mob.isAlive()) return;
        HELD.merge(mob, mob.level().getGameTime() + ticks, Math::max);
        mob.getNavigation().stop();
        StunManager.setStun(mob, ticks);
        suppressMotion(mob);
    }

    public static boolean isHeld(LivingEntity target) {
        return target instanceof Mob mob && mob.level() instanceof ServerLevel level
            && HELD.getOrDefault(mob, 0L) > level.getGameTime();
    }

    public static void suppressMotion(LivingEntity target) {
        Vec3 movement = target.getDeltaMovement();
        target.setDeltaMovement(0, Math.min(0, movement.y), 0);
    }

    static void tick() {
        HELD.entrySet().removeIf(entry -> !entry.getKey().isAlive()
            || entry.getValue() <= entry.getKey().level().getGameTime());
    }
}
