package local.dualwield.mixin;

import java.util.EnumSet;
import local.dualwield.WakizashiCombat;
import mods.flammpfeil.slashblade.ability.SlayerStyleArts;
import mods.flammpfeil.slashblade.util.InputCommand;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SlayerStyleArts.class, remap = false)
abstract class WakizashiDashMixin {
    @Inject(method = "handleSprintMove", at = @At(value = "INVOKE", shift = At.Shift.AFTER, target =
        "Lmods/flammpfeil/slashblade/ability/SlayerStyleArts;applyTrickMotionAndData(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/phys/Vec3;Ljava/lang/String;Lnet/minecraft/world/phys/Vec3;)V"))
    private void dualwield$afterNativeDash(ServerPlayer player, EnumSet<InputCommand> input,
            CallbackInfoReturnable<Boolean> callback) {
        WakizashiCombat.onNativeDash(player);
    }
}
