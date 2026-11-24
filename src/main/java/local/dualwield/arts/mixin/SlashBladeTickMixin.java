package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemSlashBlade.class)
public abstract class SlashBladeTickMixin {
    @Inject(method = "inventoryTick", at = @At("HEAD"), cancellable = true)
    private void dualwield$offhandTimeline(ItemStack stack, ServerLevel world, Entity entity,
                                            EquipmentSlot slot, CallbackInfo ci) {
        if (slot == EquipmentSlot.OFFHAND && entity instanceof ServerPlayer player
                && HandSlashArts.tickTimeline(player, InteractionHand.OFF_HAND, stack)) ci.cancel();
    }

    @Redirect(method = "lambda$inventoryTick$0", at = @At(value = "INVOKE", target =
        "Lmods/flammpfeil/slashblade/registry/combo/ComboState;tickAction(Lnet/minecraft/world/entity/LivingEntity;)V"))
    private static void dualwield$mainTimeline(ComboState combo, LivingEntity user) {
        if (!(user instanceof ServerPlayer player) || !HandSlashArts.tickTimeline(
                player, InteractionHand.MAIN_HAND, player.getItemBySlot(EquipmentSlot.MAINHAND))) {
            combo.tickAction(user);
        }
    }
}
