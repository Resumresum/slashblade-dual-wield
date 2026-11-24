package local.dualwield.mixin.client;

import local.dualwield.client.CombatClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
abstract class CombatInventoryMixin {
    @Inject(method = "setSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void dualwield$lockClientSlot(int slot, CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (CombatClient.locked() && player != null && player.getInventory() == (Object) this) callback.cancel();
    }
}
