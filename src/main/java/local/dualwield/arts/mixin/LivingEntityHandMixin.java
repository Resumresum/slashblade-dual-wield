package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityHandMixin {
    @Inject(method = "getMainHandItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$selectedArtBlade(CallbackInfoReturnable<ItemStack> cir) {
        ItemStack routed = HandSlashArts.routedMainHand((LivingEntity) (Object) this);
        if (routed != null) cir.setReturnValue(routed);
    }

    @Inject(method = "getAttributeValue", at = @At("RETURN"), cancellable = true)
    private void dualwield$selectedAttackAttribute(Holder<Attribute> attribute, CallbackInfoReturnable<Double> cir) {
        if (!attribute.equals(Attributes.ATTACK_DAMAGE)) return;
        double value = cir.getReturnValueD();
        double routed = HandSlashArts.attackAttribute((LivingEntity) (Object) this, value);
        if (routed != value) cir.setReturnValue(routed);
    }
}
