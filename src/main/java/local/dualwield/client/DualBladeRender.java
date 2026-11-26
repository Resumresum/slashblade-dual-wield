package local.dualwield.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import cn.sh1rocu.slashblade.api.RenderStateKeys;
import local.dualwield.BladeTypes;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer;
import mods.flammpfeil.slashblade.client.renderer.special.state.BladeItemRenderState;
import mods.flammpfeil.slashblade.client.renderer.util.BladeRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the offhand blade in the same pose stack used by SlashBlade's first and third person layer. */
public final class DualBladeRender {
    private static final float MODEL_SCALE = 1.0f / 128.0f;
    public static final float CRAFTED_SCALE = 0.70f;

    private DualBladeRender() {}

    private static ItemStack offhand(ArmedEntityRenderState entity) {
        return entity.mainArm == HumanoidArm.RIGHT ? entity.leftHandItemStack : entity.rightHandItemStack;
    }

    public static boolean hasPair(ArmedEntityRenderState entity) {
        return BladeTypes.isBlade(entity.getMainHandItemStack()) && BladeTypes.isBlade(offhand(entity));
    }

    public static void submitMounted(PoseStack pose, SubmitNodeCollector nodes, int light,
            ArmedEntityRenderState entity, Vector3f bodyUp) {
        if (!hasPair(entity)) return;
        var off = offhand(entity);
        var item = BladeBaseRenderer.createBladeItemRenderState(off);
        var model = BladeModelManager.getInstance().getModel(item.modelLocation);
        var bladeState = CapabilitySlashBlade.getBladeState(off).orElseThrow();
        var motion = motion(entity, off);
        pose.pushPose();
        try {
            mountBlade(pose,entity,bodyUp);
            BladeRenderState.renderOverrided(item, model, "sheath", item.textureLocation, pose, nodes, light);
            BladeRenderState.renderOverridedLuminous(item, model, "sheath_luminous", item.textureLocation, pose, nodes, light);
            if (!motion.drawn()) blade(pose, nodes, light, item, model, bladeState.isBroken());
        } finally {
            pose.popPose();
        }
    }

    private static void mountBlade(PoseStack pose, ArmedEntityRenderState entity, Vector3f bodyUp) {
        var off=offhand(entity);
        var item=BladeBaseRenderer.createBladeItemRenderState(off);
        var localUp = new Matrix4f(pose.last().pose()).invert().transformDirection(new Vector3f(bodyUp));
        var normal = new Vector3f(1, 0, 0).cross(localUp);
        if (normal.lengthSquared() <= 1e-8f) normal.set(0, 0, 1);
        normal.normalize();
        var separation = new Vector3f(normal).cross(1, 0, 0).mul(9);
        var mainMount = mountPoint(BladeBaseRenderer.createBladeItemRenderState(entity.getMainHandItemStack()).modelLocation);
        if (BladeTypes.isCraftedWakizashi(entity.getMainHandItemStack())) mainMount.mul(CRAFTED_SCALE);
        mainMount.add(separation);
        var offMount = mountPoint(item.modelLocation);

            // Keep both grips on the same vertical plane and tilt the offhand grip upward.
            pose.translate(mainMount.x, mainMount.y, mainMount.z);
            pose.mulPose(new Quaternionf().rotationAxis((float) Math.toRadians(22), normal));
            if (BladeTypes.isCraftedWakizashi(off)) pose.scale(CRAFTED_SCALE, CRAFTED_SCALE, CRAFTED_SCALE);
            pose.translate(-offMount.x, -offMount.y, -offMount.z);
    }

    static Vector3f mountPoint(Identifier model) {
        // The ends of decorated sheath meshes can include ornaments far from the actual grip.
        return switch (model.toString()) {
            case "foxextra:model/foxex/model.obj", "foxextra:model/foxex/model2.obj" -> new Vector3f(-8, 1, 0);
            // This bar has a short integral grip. Leave it clear of the avatar's arm.
            case "slashblade_addon:model/fluorescentbar/fluorescent_bar.obj" -> new Vector3f(0, 10, 0);
            case "slashblade_addon:model/wanderer/wanderer.obj" -> new Vector3f(-30, 3, 0);
            default -> new Vector3f(-40, 3, 0);
        };
    }

    public static void submitDrawn(PoseStack pose, SubmitNodeCollector nodes, int light, ArmedEntityRenderState entity) {
        ItemStack off = offhand(entity);
        var blade = CapabilitySlashBlade.getBladeState(off).orElseThrow();
        var motion = motion(entity, off);
        if (!motion.drawn()) return;
        boolean shortBlade = BladeTypes.isWakizashi(off);
        BladeItemRenderState item = BladeBaseRenderer.createBladeItemRenderState(off);
        WavefrontObject model = BladeModelManager.getInstance().getModel(item.modelLocation);
        var stroke = CombatClient.stroke(BladeStrokeRender.entityId(entity), true);
        if (stroke != null) {
            var body = new Matrix4f(pose.last().pose());
            pose.pushPose();
            try {
                var bodyUp=body.transformDirection(new Vector3f(0,-1,0));
                var mainState=CapabilitySlashBlade.getBladeState(entity.getMainHandItemStack()).orElseThrow();
                BladeStrokeRender.mount(pose,body,entity,mainState);
                mountBlade(pose,entity,bodyUp);
                BladeStrokeRender.place(pose, body, entity, off, true, stroke);
                blade(pose, nodes, light, item, model, blade.isBroken());
                BladeStrokeRender.ribbon(pose, nodes, body, entity, off, model, true, stroke);
            } finally { pose.popPose(); }
            return;
        }
        float swing = Mth.sin(motion.progress * (float) Math.PI);
        float guard = motion.guard;
        float handed = entity.mainArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        float scale = MODEL_SCALE * (BladeTypes.isCraftedWakizashi(off) ? CRAFTED_SCALE : 1.0f);

        pose.pushPose();
        try {
            var client = Minecraft.getInstance();
            if (client.player != null && client.player.getId() == motion.entityId
                    && client.options.getCameraType() == CameraType.FIRST_PERSON) {
                pose.translate(handed * 0.50f, 0.49f, -0.26f);
                pose.mulPose(Axis.YP.rotationDegrees(handed * 71.0f));
                pose.mulPose(Axis.ZP.rotationDegrees(-150.0f));
                pose.mulPose(Axis.XP.rotationDegrees(23.0f));
            } else {
                waistPose(pose, shortBlade, handed);
            }
            if (guard > 0f) {
                pose.translate(-handed * 0.20f * guard, 0.40f * guard, -0.42f * guard);
                pose.mulPose(Axis.ZP.rotationDegrees(handed * 78.0f * guard));
                pose.mulPose(Axis.XP.rotationDegrees(-38.0f * guard));
            } else if (motion.followUp) {
                // A compact rising sweep from the offhand side, using its own path.
                pose.translate(handed * (0.08f - 0.27f * swing), -0.15f + 0.26f * swing, -0.10f - 0.47f * swing);
                pose.mulPose(Axis.YP.rotationDegrees(handed * -52f * swing));
                pose.mulPose(Axis.ZP.rotationDegrees(handed * (14f + 48f * swing)));
                pose.mulPose(Axis.XP.rotationDegrees(-24f * swing));
            } else {
                pose.translate(-handed * 0.28f * swing, 0.35f * swing, -0.51f * swing);
                pose.mulPose(Axis.ZP.rotationDegrees(handed * 104.0f * swing));
                pose.mulPose(Axis.XP.rotationDegrees(-58.0f * swing));
            }
            pose.scale(scale, scale, scale);
            blade(pose, nodes, light, item, model, blade.isBroken());
        } finally {
            pose.popPose();
        }
    }

    private static Motion motion(ArmedEntityRenderState entity, ItemStack off) {
        int entityId = ((FabricRenderState) entity).getDataOrDefault(RenderStateKeys.ENTITY_ID, -1);
        float progress = CombatClient.offhandSwingProgress(entityId);
        var stroke = CombatClient.stroke(entityId, true);
        if (stroke != null) progress = Math.max(0.001f, stroke.progress());
        float guard = BladeTypes.isWakizashi(off) ? CombatClient.guardPose(entityId) : 0f;
        if (progress == 0 && entity.attackArm == entity.mainArm.getOpposite()) progress = entity.attackTime;
        return new Motion(entityId, progress, guard, progress > 0 && CombatClient.isFollowUpSwing(entityId));
    }

    private record Motion(int entityId, float progress, float guard, boolean followUp) {
        boolean drawn() { return progress > 0f || guard > 0f; }
    }

    private static void waistPose(PoseStack pose, boolean shortBlade, float handed) {
        pose.translate(0.58f, shortBlade ? 0.79f : 0.70f, -0.20f);
        pose.mulPose(Axis.YP.rotationDegrees(shortBlade ? 108.0f : 105.0f));
        pose.mulPose(Axis.ZP.rotationDegrees(shortBlade ? -25.0f : -31.0f));
        pose.mulPose(Axis.XP.rotationDegrees(handed * 8.0f));
    }

    private static void blade(PoseStack pose, SubmitNodeCollector nodes, int light,
            BladeItemRenderState item, WavefrontObject model, boolean broken) {
        var texture = item.textureLocation;
        BladeRenderState.renderOverrided(item, model, broken ? "blade_damaged" : "blade", texture, pose, nodes, light);
        BladeRenderState.renderOverridedLuminous(item, model, broken ? "blade_damaged_luminous" : "blade_luminous",
            texture, pose, nodes, light);
    }
}
