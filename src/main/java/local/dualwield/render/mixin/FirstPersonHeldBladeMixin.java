package local.dualwield.render.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import local.dualwield.BladeTypes;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class FirstPersonHeldBladeMixin {
    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$drawOffhandFromWaist(AbstractClientPlayer player, float tick, float pitch,
            InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose,
            SubmitNodeCollector nodes, int light, CallbackInfo callback) {
        if (hand == InteractionHand.OFF_HAND && BladeTypes.isBlade(stack) && BladeTypes.isBlade(player.getMainHandItem())) {
            callback.cancel();
        }
    }
}
