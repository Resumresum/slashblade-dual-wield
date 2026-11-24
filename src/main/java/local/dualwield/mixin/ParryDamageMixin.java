package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class ParryDamageMixin {
    @Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
    private void dualwield$counterProtection(ServerLevel level, DamageSource source, float damage,
            CallbackInfoReturnable<Boolean> callback) {
        if (DualWieldCombat.counterInvulnerable((LivingEntity) (Object) this, source)) callback.setReturnValue(false);
    }

    @ModifyVariable(method = "hurtServer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float dualwield$guardDamage(float damage, ServerLevel level, DamageSource source) {
        return DualWieldCombat.guardDamage((LivingEntity) (Object) this, source, damage);
    }
}
