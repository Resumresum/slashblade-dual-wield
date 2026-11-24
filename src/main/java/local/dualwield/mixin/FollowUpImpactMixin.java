package local.dualwield.mixin;

import java.util.List;
import java.util.function.Consumer;
import local.dualwield.FollowUp;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntitySlashEffect.class)
abstract class FollowUpImpactMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE", target =
        "Lmods/flammpfeil/slashblade/util/AttackManager;areaAttack(Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Consumer;FZZZLjava/util/List;)Ljava/util/List;"))
    private List<Entity> dualwield$followOriginalAction(LivingEntity user, Consumer<LivingEntity> beforeHit,
            float ratio, boolean forceHit, boolean resetHit, boolean mute, List<Entity> excluded) {
        return FollowUp.withSlash((EntitySlashEffect) (Object) this,
            () -> AttackManager.areaAttack(user, beforeHit, ratio, forceHit, resetHit, mute, excluded));
    }
}
