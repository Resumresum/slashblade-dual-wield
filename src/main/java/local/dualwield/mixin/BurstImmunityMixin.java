package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import local.dualwield.BurstTargetHold;
import local.dualwield.FollowUp;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
abstract class BurstImmunityMixin {
    @Inject(method = "knockback", at = @At("HEAD"), cancellable = true)
    private void dualwield$ignoreKnockback(double strength, double x, double z, CallbackInfo callback) {
        var target = (LivingEntity) (Object) this;
        if (DualWieldCombat.active(target) || BurstTargetHold.isHeld(target) || FollowUp.suppressesKnockback(target)) {
            callback.cancel();
        }
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3 dualwield$holdHitMob(Vec3 input) {
        var target = (LivingEntity) (Object) this;
        if (!BurstTargetHold.isHeld(target)) return input;
        BurstTargetHold.suppressMotion(target);
        return Vec3.ZERO;
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
        at = @At("HEAD"), cancellable = true)
    private void dualwield$ignoreMovementControl(MobEffectInstance effect, Entity source,
            CallbackInfoReturnable<Boolean> callback) {
        if ((effect.getEffect().equals(MobEffects.LEVITATION) || effect.getEffect().equals(MobEffects.SLOWNESS))
                && DualWieldCombat.active((LivingEntity) (Object) this)) callback.setReturnValue(false);
    }
}
