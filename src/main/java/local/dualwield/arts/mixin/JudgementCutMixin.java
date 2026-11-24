package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityJudgementCut.class)
abstract class JudgementCutMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void dualwield$beginTick(CallbackInfo callback) {
        HandSlashArts.beginDelayed(this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void dualwield$endTick(CallbackInfo callback) {
        HandSlashArts.endDelayed(this);
    }
}
