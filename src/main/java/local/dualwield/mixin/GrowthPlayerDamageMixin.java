package local.dualwield.mixin;

import local.dualwield.CombatGrowth;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player overrides actuallyHurt instead of calling the LivingEntity implementation. */
@Mixin(Player.class)
abstract class GrowthPlayerDamageMixin {
    @Inject(method = "actuallyHurt", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/player/Player;getCombatTracker()Lnet/minecraft/world/damagesource/CombatTracker;"))
    private void dualwield$offhandParticipated(ServerLevel level, DamageSource source, float damage,
            CallbackInfo callback) {
        CombatGrowth.damaged((LivingEntity) (Object) this, source);
    }
}
