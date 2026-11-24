package local.dualwield.mixin;

import local.dualwield.CombatGrowth;
import local.dualwield.DualWieldCombat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class GrowthDamageMixin {
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/LivingEntity;getCombatTracker()Lnet/minecraft/world/damagesource/CombatTracker;"))
    private void dualwield$offhandParticipated(ServerLevel level, DamageSource source, float damage,
            CallbackInfo callback) {
        CombatGrowth.damaged((LivingEntity) (Object) this, source);
    }

    @Inject(method = "onEquipItem", at = @At("HEAD"))
    private void dualwield$equipmentChanged(EquipmentSlot slot, ItemStack before, ItemStack after,
            CallbackInfo callback) {
        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND) && before != after
                && (LivingEntity) (Object) this instanceof ServerPlayer player) {
            CombatGrowth.equipmentChanged(player);
            DualWieldCombat.equipmentChanged(player);
        }
    }
}
