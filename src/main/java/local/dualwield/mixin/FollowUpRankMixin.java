package local.dualwield.mixin;

import local.dualwield.FollowUp;
import mods.flammpfeil.slashblade.event.handler.RankPointHandler;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RankPointHandler.class)
abstract class FollowUpRankMixin {
    @Inject(method = "onLivingDeathEvent", at = @At("HEAD"), cancellable = true)
    private void dualwield$noAdditionalRank(LivingEntity victim, DamageSource source, float base,
            float taken, boolean blocked, CallbackInfo callback) {
        if (FollowUp.suppressesRank(victim)) callback.cancel();
    }
}
