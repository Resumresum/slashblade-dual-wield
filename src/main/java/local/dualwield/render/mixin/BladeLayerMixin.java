package local.dualwield.render.mixin;

import cn.sh1rocu.slashblade.api.RenderStateKeys;
import com.mojang.blaze3d.vertex.PoseStack;
import local.dualwield.BladeTypes;
import local.dualwield.client.CombatClient;
import local.dualwield.client.DualBladeRender;
import local.dualwield.client.WakizashiArtPose;
import jp.nyatla.nymmd.MmdMotionPlayerGL2;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.client.renderer.layers.LayerMainBlade;
import mods.flammpfeil.slashblade.init.DefaultResources;
import mods.flammpfeil.slashblade.item.SwordType;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.combo.ComboState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LayerMainBlade.class, remap = false)
public abstract class BladeLayerMixin {
    @Unique
    private static final String MODEL_RENDER =
        "Lmods/flammpfeil/slashblade/client/renderer/util/BladeRenderState;renderOverrided(Lmods/flammpfeil/slashblade/client/renderer/special/state/BladeItemRenderState;Lmods/flammpfeil/slashblade/client/renderer/model/obj/WavefrontObject;Ljava/lang/String;Lnet/minecraft/resources/Identifier;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    @Unique
    private final Vector3f dualwield$bodyUp = new Vector3f();

    @Unique
    private float dualwield$chargeHold;

    @Unique
    private float dualwield$artDraw;

    @Inject(method = "renderOffhandItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$renderOffhand(PoseStack pose, SubmitNodeCollector nodes, int light,
                                         EntityRenderState state, CallbackInfo ci) {
        if (state instanceof ArmedEntityRenderState armed && DualBladeRender.hasPair(armed)) {
            DualBladeRender.submitDrawn(pose, nodes, light, armed);
            ci.cancel();
        }
    }

    @Inject(method = "lambda$submit$1", at = @At("HEAD"))
    private void dualwield$captureBodyUp(ArmedEntityRenderState state, PoseStack pose, float yOffset, double motionScale,
            ISlashBladeState blade, ItemStack stack, double modelScale, SubmitNodeCollector nodes, int light,
            MmdMotionPlayerGL2 motion, CallbackInfo ci) {
        // The humanoid render stack has already flipped its vertical axis.
        pose.last().pose().transformDirection(dualwield$bodyUp.set(0, -1, 0));
        dualwield$artDraw = -1f;
        dualwield$chargeHold = 0f;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || client.getCameraEntity() != player || !client.options.getCameraType().isFirstPerson()
                || ((FabricRenderState) state).getDataOrDefault(RenderStateKeys.ENTITY_ID, -1) != player.getId()
                || !BladeTypes.isWakizashi(stack)) return;
        dualwield$artDraw = CombatClient.shortArtDrawProgress();
        if (dualwield$artDraw >= 0f) {
            dualwield$chargeHold = 1f - WakizashiArtPose.recovery(dualwield$artDraw);
        } else if (player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && !blade.isBroken() && !blade.isSealed() && SwordType.from(stack).contains(SwordType.ENCHANTED)) {
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            float progress = (player.getTicksUsingItem() + partialTick - 2f)
                / Math.max(1f, blade.getFullChargeTicks(player) - 2f);
            dualwield$chargeHold = WakizashiArtPose.ease(progress);
        }
    }

    @ModifyVariable(method = "lambda$submit$1", at = @At("STORE"), ordinal = 0)
    private ComboState dualwield$holdShortBladeDuringCharge(ComboState combo) {
        return dualwield$chargeHold > 0f || dualwield$artDraw >= 0f ? ComboStateRegistry.STANDBY : combo;
    }

    @Inject(method = "lambda$submit$1", at = @At(value = "INVOKE", ordinal = 0, target = MODEL_RENDER))
    private void dualwield$scaleMainBlade(ArmedEntityRenderState state, PoseStack pose, float yOffset,
            double motionScale, ISlashBladeState blade, ItemStack stack, double modelScale, SubmitNodeCollector nodes,
            int light, MmdMotionPlayerGL2 motion, CallbackInfo ci) {
        dualwield$scaleShortMain(pose, stack);
        if (dualwield$chargeHold > 0f || dualwield$artDraw >= 0f) {
            WakizashiArtPose.place(pose, blade.getModel().orElse(DefaultResources.resourceDefaultModel), state.mainArm,
                dualwield$chargeHold, dualwield$artDraw);
        }
    }

    @Inject(method = "lambda$submit$1", at = @At(value = "INVOKE", ordinal = 1, target = MODEL_RENDER))
    private void dualwield$mountAtMainScabbard(ArmedEntityRenderState state, PoseStack pose, float yOffset,
            double motionScale, ISlashBladeState blade, ItemStack stack, double modelScale, SubmitNodeCollector nodes,
            int light, MmdMotionPlayerGL2 motion, CallbackInfo ci) {
        DualBladeRender.submitMounted(pose, nodes, light, state, dualwield$bodyUp);
        dualwield$scaleShortMain(pose, stack);
        if (dualwield$chargeHold > 0f) {
            WakizashiArtPose.place(pose, blade.getModel().orElse(DefaultResources.resourceDefaultModel), state.mainArm,
                dualwield$chargeHold, -1f);
        }
    }

    @Unique
    private static void dualwield$scaleShortMain(PoseStack pose, ItemStack stack) {
        if (BladeTypes.isCraftedWakizashi(stack)) {
            float scale = DualBladeRender.CRAFTED_SCALE;
            pose.scale(scale, scale, scale);
        }
    }
}
