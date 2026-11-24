package local.dualwield.mixin.client;

import local.dualwield.CombatPackets;
import local.dualwield.WakizashiCombat;
import local.dualwield.client.CombatClient;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class CombatMinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void dualwield$noOtherAttackDuringBurst(CallbackInfoReturnable<Boolean> callback) {
        if (CombatClient.locked()) {
            callback.setReturnValue(false);
            return;
        }
        var client = (Minecraft) (Object) this;
        if (client.player != null && WakizashiCombat.canAttack(client.player) && !client.player.isUsingItem()
                && (client.hitResult == null || client.hitResult.getType() != HitResult.Type.BLOCK)
                && ClientPlayNetworking.canSend(CombatPackets.Press.TYPE)) {
            ClientPlayNetworking.send(new CombatPackets.Press(CombatPackets.PRIMARY));
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$noHeldUseDuringSpecialAction(CallbackInfo callback) {
        if (CombatClient.locked()) callback.cancel();
    }
}
