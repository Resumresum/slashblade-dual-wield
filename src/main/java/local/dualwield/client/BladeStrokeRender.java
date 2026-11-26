package local.dualwield.client;

import cn.sh1rocu.slashblade.api.RenderStateKeys;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import local.dualwield.BladeMotion;
import local.dualwield.BladeTypes;
import mods.flammpfeil.slashblade.client.renderer.model.obj.WavefrontObject;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Map;
import java.util.WeakHashMap;

/** Blade and ribbon share the same sampled path; the ribbon never causes damage. */
public final class BladeStrokeRender {
    private static final Identifier WHITE = Identifier.parse("slashblade_dual_wield:textures/stroke.png");
    private static final Map<WavefrontObject, Geometry> GEOMETRY = new WeakHashMap<>();
    private static final Map<Integer, RestMount> REST_MOUNTS = new java.util.HashMap<>();
    private static Object mountLevel;
    private BladeStrokeRender() {}

    public static void clearMounts() {
        REST_MOUNTS.clear();
        mountLevel = null;
    }

    public static int entityId(ArmedEntityRenderState entity) {
        return ((FabricRenderState) entity).getDataOrDefault(RenderStateKeys.ENTITY_ID, -1);
    }

    public static boolean active(ArmedEntityRenderState entity) {
        int id = entityId(entity);
        return CombatClient.stroke(id, false) != null || CombatClient.stroke(id, true) != null;
    }

    public static boolean firstPerson(ArmedEntityRenderState entity) {
        var client = Minecraft.getInstance();
        return client.player != null && client.player.getId() == entityId(entity)
            && client.options.getCameraType().isFirstPerson();
    }

    public static Matrix4f matrix(Matrix4f body, ArmedEntityRenderState entity, ItemStack stack,
            boolean offhand, CombatClient.Stroke stroke, float progress, boolean modelScale) {
        var frame = stroke.pose(progress);
        float side = offhand ? -1f : 1f;
        if (entity.mainArm == HumanoidArm.LEFT) side *= -1f;
        boolean first = firstPerson(entity);
        var item = mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer.createBladeItemRenderState(stack);
        var model = mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(item.modelLocation);
        var geometry = geometry(model, item.modelLocation);
        if(first) frame=BladeMotion.viewPose(frame,BladeTypes.isWakizashi(stack),geometry.hiltReach);
        var grip = new Vector3f(frame.gx() * side, frame.gy(), frame.gz());
        var direction = new Vector3f(frame.dx() * side, frame.dy(), frame.dz()).normalize();
        var edge = new Vector3f(frame.ex() * side, frame.ey(), frame.ez()).normalize();
        if (offhand) {
            grip.y -= 0.055f;
            grip.z -= 0.035f;
        }
        Matrix4f result;
        if (first) {
            result = new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        } else {
            result = new Matrix4f(body);
            grip.set(grip.x, 0.67f - grip.y, grip.z);
            direction.y *= -1f;
            edge.y *= -1f;
        }
        // Most SlashBlade tips point along negative X, but sampled custom models need their own axis.
        var modelForward = new Vector3f(geometry.tip).sub(geometry.grip).normalize();
        result.translate(grip).rotate(align(modelForward, geometry.edge, direction, edge));
        if (modelScale) {
            float scale = (1f / 128f) * (BladeTypes.isCraftedWakizashi(stack) ? DualBladeRender.CRAFTED_SCALE : 1f);
            var mount = geometry.grip;
            result.scale(scale).translate(-mount.x, -mount.y, -mount.z);
        }
        return result;
    }

    public static void place(PoseStack pose, Matrix4f body, ArmedEntityRenderState entity,
            ItemStack stack, boolean offhand, CombatClient.Stroke stroke) {
        var target = matrix(body, entity, stack, offhand, stroke, stroke.progress(), true);
        float weight = stroke.weight();
        if (weight < 1f) {
            var item = mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer.createBladeItemRenderState(stack);
            var model = mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager.getInstance().getModel(item.modelLocation);
            var grip = geometry(model, item.modelLocation).grip;
            var nativePose = pose.last().pose();
            var position = nativePose.transformPosition(new Vector3f(grip))
                .lerp(target.transformPosition(new Vector3f(grip)), weight);
            var rotation = nativePose.getUnnormalizedRotation(new Quaternionf()).normalize()
                .slerp(target.getUnnormalizedRotation(new Quaternionf()).normalize(), weight);
            var scale = nativePose.getScale(new Vector3f()).lerp(target.getScale(new Vector3f()), weight);
            target.translationRotateScale(position, rotation, scale).translate(-grip.x,-grip.y,-grip.z);
        }
        pose.last().pose().set(target);
        pose.last().normal().rotation(pose.last().pose().getUnnormalizedRotation(new Quaternionf()).normalize());
    }

    public static void rememberMount(PoseStack pose, Matrix4f body, ArmedEntityRenderState entity,
            mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState blade) {
        var level = Minecraft.getInstance().level;
        if (level != mountLevel) { REST_MOUNTS.clear(); mountLevel = level; }
        var combo = ((FabricRenderState) entity).getDataOrDefault(RenderStateKeys.COMBO_STATE,
            mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE);
        if (combo != mods.flammpfeil.slashblade.registry.ComboStateRegistry.NONE
                && combo != mods.flammpfeil.slashblade.registry.ComboStateRegistry.STANDBY) return;
        if (REST_MOUNTS.size() > 128) REST_MOUNTS.clear();
        REST_MOUNTS.put(entityId(entity), new RestMount(blade.getModel(),
            new Matrix4f(body).invert().mul(pose.last().pose())));
    }

    public static void mount(PoseStack pose, Matrix4f body, ArmedEntityRenderState entity,
            mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState blade) {
        var rest = REST_MOUNTS.get(entityId(entity));
        if (rest != null && rest.model.equals(blade.getModel())) {
            var matrix = new Matrix4f(body).mul(rest.local);
            pose.last().pose().set(matrix);
            pose.last().normal().rotation(matrix.getUnnormalizedRotation(new Quaternionf()).normalize());
            return;
        }
        // Fallback only for an entity first seen mid-attack; idle captures preserve the established mount.
        var mat = new Matrix4f(body);
        switch (blade.getCarryType()) {
            case KATANA -> mat.translate(0.25f, 0.625f, -0.55f)
                .rotateZYX((float) Math.PI, (float) Math.PI / 2f, (float) Math.PI / 12f);
            case NINJA -> mat.translate(-0.5f, -0.5f, 0.2f).rotateZYX(-2.094395f, 0, (float) Math.PI);
            case RNINJA -> mat.translate(0.5f, -0.5f, 0.2f).rotateZ(-1.047198f);
            case PSO2 -> mat.translate(1f, 0.375f, 0.2f).rotateZ(-0.122173f);
            default -> mat.translate(0.25f, 0.625f, -0.55f)
                .rotateZYX(0, (float) Math.PI / 2f, (float) Math.PI / 12f);
        }
        pose.last().pose().set(mat.scale(1f / 128f));
        pose.last().normal().rotation(mat.getUnnormalizedRotation(new Quaternionf()).normalize());
    }

    public static void ribbon(PoseStack pose, SubmitNodeCollector nodes, Matrix4f body,
            ArmedEntityRenderState entity, ItemStack stack, WavefrontObject model, boolean offhand,
            CombatClient.Stroke stroke) {
        float now = stroke.progress();
        float strikeStart = BladeMotion.strikeStart(stroke.kind());
        float strikeEnd = BladeMotion.strikeEnd(stroke.kind(), stroke.beat());
        float fadeEnd = Math.min(1f, strikeEnd + 0.18f);
        if (now <= strikeStart || now >= fadeEnd) return;
        float end = Math.min(now, strikeEnd);
        float begin = Math.max(strikeStart, end - 0.22f);
        if (end <= begin) return;
        var item = mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer.createBladeItemRenderState(stack);
        var geometry = geometry(model, item.modelLocation);
        var grip = geometry.grip;
        var tip = geometry.tip;
        var inner = new Vector3f(grip).lerp(tip, 0.34f);
        var points = new Vector3f[26];
        for (int i = 0; i <= 12; i++) {
            float phase = begin + (end - begin) * i / 12f;
            var mat = matrix(body, entity, stack, offhand, stroke, phase, true);
            points[i * 2] = mat.transformPosition(new Vector3f(inner));
            points[i * 2 + 1] = mat.transformPosition(new Vector3f(tip));
        }
        float fade = now > strikeEnd ? 1f - (now - strikeEnd) / (fadeEnd-strikeEnd) : 1f;
        int rgb = stroke.color() & 0xffffff;
        int red = 120 + ((rgb >> 16) & 255) * 135 / 255;
        int green = 120 + ((rgb >> 8) & 255) * 135 / 255;
        int blue = 120 + (rgb & 255) * 135 / 255;
        pose.pushPose();
        pose.last().pose().identity();
        pose.last().normal().identity();
        nodes.submitCustomGeometry(pose, RenderTypes.entityTranslucentEmissive(WHITE), (entry, vertices) -> {
            for (int i = 0; i < 12; i++) {
                int a = (int) (fade * 185f * (i + 1) / 12f);
                vertex(vertices, entry, points[i * 2], red, green, blue, 0, 0, 0);
                vertex(vertices, entry, points[i * 2 + 1], red, green, blue, a, 0, 1);
                vertex(vertices, entry, points[i * 2 + 3], 255, 255, 255, a, 1, 1);
                vertex(vertices, entry, points[i * 2 + 2], red, green, blue, 0, 1, 0);
            }
        });
        pose.popPose();
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose, Vector3f point,
            int r, int g, int b, int alpha, float u, float v) {
        out.addVertex(pose, point).setColor(r, g, b, alpha).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xf000f0).setNormal(pose, 0, 0, 1);
    }

    private static Geometry geometry(WavefrontObject model, Identifier name) {
        return GEOMETRY.computeIfAbsent(model, mesh -> {
            var grip = DualBladeRender.mountPoint(name);
            var tip = new Vector3f(grip).add(-128f, 0, 0);
            float farthest = 0;
            for (var group : mesh.groupObjects) {
                if (!group.name.equals("blade")) continue;
                for (var face : group.faces) for (var vertex : face.vertices) {
                    float distance = grip.distanceSquared(vertex.x, vertex.y, vertex.z);
                    if (distance > farthest) {
                        farthest = distance;
                        tip.set(vertex.x, vertex.y, vertex.z);
                    }
                }
            }
            var forward = new Vector3f(tip).sub(grip).normalize();
            float hiltReach=0;
            for(var group:mesh.groupObjects) {
                if(!group.name.equals("blade")) continue;
                for(var face:group.faces) for(var vertex:face.vertices) {
                    var relative=new Vector3f(vertex.x,vertex.y,vertex.z).sub(grip);
                    if(relative.dot(forward)<0) hiltReach=Math.max(hiltReach,relative.length()/128f);
                }
            }
            // Standard SlashBlade meshes have a sharpened +Y side (the +Y vertices meet at Z=0).
            // ponytail: unusual rotated/multi-blade meshes need an explicit model edge profile.
            var edge = new Vector3f(0,1,0).fma(-forward.y,forward);
            if(edge.lengthSquared()<1e-6f) edge.set(0,0,1).fma(-forward.z,forward);
            return new Geometry(grip, tip, edge.normalize(),hiltReach);
        });
    }

    public static Quaternionf align(Vector3f sourceForward, Vector3f sourceEdge, Vector3f forward, Vector3f edge) {
        var source = new Matrix3f().setColumn(0,sourceForward).setColumn(1,sourceEdge)
            .setColumn(2,new Vector3f(sourceForward).cross(sourceEdge));
        var target = new Matrix3f().setColumn(0,forward).setColumn(1,edge)
            .setColumn(2,new Vector3f(forward).cross(edge));
        return target.mul(source.transpose()).getNormalizedRotation(new Quaternionf());
    }

    private record Geometry(Vector3f grip, Vector3f tip, Vector3f edge, float hiltReach) {}
    private record RestMount(java.util.Optional<Identifier> model, Matrix4f local) {}
}
