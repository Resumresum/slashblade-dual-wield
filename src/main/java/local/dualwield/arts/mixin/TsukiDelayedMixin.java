package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep the original blade selected while Tsuki's two-stage manager fires later. */
@Pseudo
@Mixin(targets = "local.stage1.tsuki.TsukiDrive", remap = false)
public abstract class TsukiDelayedMixin {
    @Inject(method = "configure", at = @At("HEAD"), remap = false, require = 0)
    private void dualwield$capture(LivingEntity user, ItemStack blade, double damage, boolean multi,
                                   float roll, float speed, int lifetime, int color, CallbackInfo ci) {
        HandSlashArts.captureDelayed(this);
    }

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void dualwield$beginTick(CallbackInfo ci) { HandSlashArts.beginDelayed(this); }

    @Inject(method = "tick", at = @At("RETURN"), remap = false, require = 0)
    private void dualwield$endTick(CallbackInfo ci) { HandSlashArts.endDelayed(this); }
}
