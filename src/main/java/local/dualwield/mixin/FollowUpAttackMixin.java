package local.dualwield.mixin;

import local.dualwield.FollowUp;
import mods.flammpfeil.slashblade.util.AttackHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AttackHelper.class)
abstract class FollowUpAttackMixin {
    @Redirect(method = "attack", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private static boolean dualwield$followSuccessfulHit(Entity target, ServerLevel level, DamageSource source,
            float damage, LivingEntity attacker, Entity originalTarget, float ratio) {
        float extraDamage = FollowUp.extraDamage(attacker, target, ratio);
        boolean hit = target.hurtServer(level, source, damage);
        if (hit) FollowUp.hit(attacker, target, extraDamage);
        return hit;
    }
}
