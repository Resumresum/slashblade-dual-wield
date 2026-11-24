package local.dualwield.mixin;

import local.dualwield.FollowUp;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.util.AttackManager;
import mods.flammpfeil.slashblade.util.KnockBacks;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AttackManager.class)
abstract class FollowUpSlashMixin {
    @Inject(method = "doSlash(Lnet/minecraft/world/entity/LivingEntity;FILnet/minecraft/world/phys/Vec3;ZZDLmods/flammpfeil/slashblade/util/KnockBacks;)Lmods/flammpfeil/slashblade/entity/EntitySlashEffect;",
        at = @At("RETURN"))
    private static void dualwield$markNativeSlash(LivingEntity player, float roll, int color, Vec3 offset,
            boolean mute, boolean critical, double ratio, KnockBacks knockback,
            CallbackInfoReturnable<EntitySlashEffect> callback) {
        FollowUp.markSlash(player, callback.getReturnValue());
    }
}
