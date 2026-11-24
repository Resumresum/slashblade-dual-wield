package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.SlashBladeState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SlashBladeState.class)
public abstract class SlashBladeDamageMixin {
    @Inject(method = "getBaseAttackModifier", at = @At("RETURN"), cancellable = true)
    private void dualwield$scaleArtDamage(CallbackInfoReturnable<Float> cir) {
        float multiplier = HandSlashArts.damageMultiplier();
        if (multiplier != 1.0f) cir.setReturnValue(cir.getReturnValue() * multiplier);
    }
}
