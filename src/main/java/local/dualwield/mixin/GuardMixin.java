package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import mods.flammpfeil.slashblade.ability.Guard;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Guard.class)
abstract class GuardMixin {
    @Inject(method = "onLivingAttack", at = @At("HEAD"), cancellable = true)
    private void dualwield$keepBurstVulnerable(LivingEntity victim, DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> callback) {
        if (DualWieldCombat.active(victim) || DualWieldCombat.guarding(victim)) callback.setReturnValue(true);
    }
}
