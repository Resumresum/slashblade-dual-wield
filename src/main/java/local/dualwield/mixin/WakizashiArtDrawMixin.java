package local.dualwield.mixin;

import local.dualwield.BladeTypes;
import local.dualwield.CombatPackets;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = ItemSlashBlade.class, remap = false)
abstract class WakizashiArtDrawMixin {
    @Redirect(method = "lambda$releaseUsing$0", at = @At(value = "INVOKE", target =
        "Lmods/flammpfeil/slashblade/capability/slashblade/ISlashBladeState;doChargeAction(Lnet/minecraft/world/entity/LivingEntity;I)Lnet/minecraft/resources/Identifier;"))
    private static Identifier dualwield$animateReleasedShortArt(ISlashBladeState blade, LivingEntity user, int elapsed,
            ItemStack stack, LivingEntity originalUser, int originalElapsed, ISlashBladeState originalBlade) {
        Identifier result = blade.doChargeAction(user, elapsed);
        if (user instanceof ServerPlayer player && stack == player.getMainHandItem() && BladeTypes.isWakizashi(stack)
                && elapsed >= blade.getFullChargeTicks(user) && !result.equals(ComboStateRegistry.getId(ComboStateRegistry.NONE))) {
            CombatPackets.drawShortArt(player);
        }
        return result;
    }
}
