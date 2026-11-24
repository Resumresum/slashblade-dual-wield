package local.dualwield.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import local.dualwield.BladeTypes;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
abstract class HeldBladeLayerMixin {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$drawOffhandAtWaist(ArmedEntityRenderState entity, ItemStackRenderState renderState,
            ItemStack stack, HumanoidArm arm, PoseStack pose, SubmitNodeCollector nodes, int light,
            CallbackInfo callback) {
        if (arm != entity.mainArm && BladeTypes.isBlade(stack) && BladeTypes.isBlade(entity.getMainHandItemStack())) {
            callback.cancel();
        }
    }
}
