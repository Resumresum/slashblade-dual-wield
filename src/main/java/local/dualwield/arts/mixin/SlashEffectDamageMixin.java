package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntitySlashEffect.class)
public abstract class SlashEffectDamageMixin {
    @ModifyVariable(method = "setDamage", at = @At("HEAD"), argsOnly = true)
    private double dualwield$scaleSlashEffect(double damage) {
        HandSlashArts.captureDelayed(this);
        return damage * HandSlashArts.damageMultiplier();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dualwield$beginEffectTick(CallbackInfo callback) {
        HandSlashArts.beginDelayed(this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dualwield$endEffectTick(CallbackInfo callback) {
        HandSlashArts.endDelayed(this);
    }
}
