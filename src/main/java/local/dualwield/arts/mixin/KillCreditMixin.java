package local.dualwield.arts.mixin;

import mods.flammpfeil.slashblade.event.handler.KillCounter;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(KillCounter.class)
abstract class KillCreditMixin {
    @Redirect(method = "onLivingDeathEvent", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/LivingEntity;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dualwield$killToEquippedMain(LivingEntity killer) {
        return killer.getItemBySlot(EquipmentSlot.MAINHAND);
    }

    @Redirect(method = "onXPDropping", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/player/Player;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack dualwield$soulsToEquippedMain(Player killer) {
        return killer.getItemBySlot(EquipmentSlot.MAINHAND);
    }
}
