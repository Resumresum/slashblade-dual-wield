package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stage 1 Tsuki calls pay during both immediate and combo based arts. */
@Pseudo
@Mixin(targets = "local.stage1.tsuki.TsukiArts", remap = false)
public abstract class TsukiPaymentMixin {
    @Inject(method = "pay", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private static void dualwield$payOnce(LivingEntity user, ItemStack blade, int cost,
                                          int fallback, CallbackInfo ci) {
        if (HandSlashArts.suppressInternalPayment()) ci.cancel();
    }
}
