package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import mods.flammpfeil.slashblade.util.TargetSelector;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TargetSelector.class)
abstract class BurstArtReachMixin {
    @Inject(method = "getResolvedReach", at = @At("RETURN"), cancellable = true)
    private static void dualwield$keepArtsInReach(LivingEntity user, CallbackInfoReturnable<Double> callback) {
        double reach = callback.getReturnValueD();
        double resolved = DualWieldCombat.burstArtReach(user, reach);
        if (resolved != reach) callback.setReturnValue(resolved);
    }
}
