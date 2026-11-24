package local.dualwield.mixin;

import cn.sh1rocu.slashblade.api.event.LivingExperienceDropEvent;
import local.dualwield.CombatGrowth;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.event.handler.KillCounter;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KillCounter.class)
abstract class GrowthRewardMixin {
    @Inject(method = "onLivingDeathEvent", at = @At("HEAD"))
    private void dualwield$beginKill(LivingEntity target, DamageSource source, CallbackInfo callback) {
        CombatGrowth.beginReward(target, source.getEntity() instanceof LivingEntity killer ? killer : null);
    }

    @Inject(method = "onXPDropping", at = @At("HEAD"))
    private void dualwield$beginSouls(LivingExperienceDropEvent event, CallbackInfo callback) {
        CombatGrowth.beginReward(event.getEntity(), event.getAttackingPlayer());
    }

    @Inject(method = {"onLivingDeathEvent", "onXPDropping"}, at = @At("RETURN"))
    private void dualwield$endReward(CallbackInfo callback) {
        CombatGrowth.endReward();
    }

    @Redirect(method = "lambda$onLivingDeathEvent$0", at = @At(value = "INVOKE", target =
        "Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;setKillCount(I)V"))
    private static void dualwield$shareKills(ISlashBladeState state, int total, ItemStack blade,
            ISlashBladeState originalState) {
        CombatGrowth.reward(blade, state, total, false);
    }

    @Redirect(method = "lambda$onXPDropping$1", at = @At(value = "INVOKE", target =
        "Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;setProudSoulCount(I)V"))
    private static void dualwield$shareSouls(ISlashBladeState state, int total, ItemStack blade, int souls,
            ISlashBladeState originalState) {
        CombatGrowth.reward(blade, state, total, true);
    }
}
