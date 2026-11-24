package local.dualwield.mixin;

import java.util.Map;
import local.dualwield.DualWieldCombat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ServerExplosion.class)
abstract class BurstExplosionMixin {
    @Redirect(method = "hurtEntities", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/Entity;push(Lnet/minecraft/world/phys/Vec3;)V"))
    private void dualwield$skipExplosionPush(Entity entity, Vec3 impulse) {
        if (!(entity instanceof LivingEntity living) || !DualWieldCombat.active(living)) entity.push(impulse);
    }

    @Redirect(method = "hurtEntities", at = @At(value = "INVOKE", target =
        "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
    private Object dualwield$skipClientImpulse(Map<Object, Object> impulse, Object target, Object movement) {
        if (target instanceof LivingEntity living && DualWieldCombat.active(living)) return null;
        return impulse.put(target, movement);
    }
}
