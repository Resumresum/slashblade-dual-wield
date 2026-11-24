package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import mods.flammpfeil.slashblade.ability.SuperSlashArts;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SuperSlashArts.class)
abstract class SuperArtMixin {
    @Inject(method = "releaseSSA", at = @At("HEAD"), cancellable = true)
    private static void dualwield$noExtraSuperArt(ServerPlayer player, CallbackInfo callback) {
        if (DualWieldCombat.active(player)) callback.cancel();
    }
}
