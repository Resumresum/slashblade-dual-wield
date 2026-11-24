package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.util.AttackManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(AttackManager.class)
abstract class VoidSlashDamageMixin {
    @ModifyVariable(method = "doAttackWith", at = @At("HEAD"), argsOnly = true)
    private static float dualwield$scaleVoidImpact(float damage) {
        return damage * HandSlashArts.voidImpactMultiplier();
    }
}
