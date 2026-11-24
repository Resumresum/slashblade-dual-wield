package local.dualwield.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class WakizashiArtPose {
    // The handle rises 20° above horizontal and points 30° out from the forward vertical plane.
    private static final double HELD_RISE = Math.sin(Math.toRadians(20));
    private static final double HELD_SIDE = Math.sin(Math.toRadians(30));
    private static final float HELD_ROLL = (float) Math.toDegrees(Math.atan2(HELD_RISE, HELD_SIDE));
    private static final float HELD_DEPTH_ANGLE = (float) Math.toDegrees(Math.acos(Math.hypot(HELD_SIDE, HELD_RISE)));
    private static final float STRIKE_END = 0.47f;
    private static final float RECOVERY_START = 0.81f;
    private static final float FORWARD_TURN = (float) Math.toRadians(30);

    // Grip positions are fractions of the viewport. The angle is the direction from the guard to the handle.
    private static final Frame[] DRAW = {
        new Frame(0f, 0.18f, 0.27f, 1.85f, HELD_ROLL, HELD_DEPTH_ANGLE),
        new Frame(0.13f, 0.29f, 0.25f, 2.02f, 20f, 34f),
        new Frame(0.27f, 0.405f, 0.24f, 2.18f, 11f, 14f),
        new Frame(0.40f, 0.50f, 0.32f, 2.30f, -35f, 4f),
        new Frame(0.53f, 0.60f, 0.385f, 2.37f, -86f, 2f),
        new Frame(0.67f, 0.69f, 0.51f, 2.42f, -132f, 0f),
        new Frame(0.82f, 0.75f, 0.66f, 2.42f, -178f, 0f),
        new Frame(1f, 0.77f, 0.72f, 2.42f, -204f, 0f)
    };

    private WakizashiArtPose() {}

    public static float ease(float position) {
        float t = Mth.clamp(position, 0f, 1f);
        return t * t * (3f - 2f * t);
    }

    public static float recovery(float draw) {
        return ease((draw - RECOVERY_START) / (1f - RECOVERY_START));
    }

    public static void place(PoseStack pose, Identifier model, HumanoidArm hand, float hold, float draw) {
        var client = Minecraft.getInstance();
        var render = client.gameRenderer.getGameRenderState();
        var camera = render.levelRenderState.cameraRenderState;
        if (camera == null || hold <= 0f && draw < 0f) return;
        float swing = Mth.clamp(draw / STRIKE_END, 0f, 1f);
        var frame = draw >= 0f ? sample(swing) : DRAW[0];
        boolean leftHanded = hand == HumanoidArm.LEFT;
        float x = leftHanded ? 1f - frame.x : frame.x;
        float angle = leftHanded ? 180f - frame.angle : frame.angle;
        var view = RenderSystem.getModelViewMatrix();
        var main = pose.last();
        var inView = new Matrix4f(view).mul(main.pose());
        var grip = DualBladeRender.mountPoint(model);
        var scale = inView.getScale(new Vector3f());

        float depth = frame.depth;
        float heightPerDepth = (float) Math.tan(Math.toRadians(camera.hudFov * 0.5f));
        float widthPerDepth = heightPerDepth * render.windowRenderState.width / Math.max(1f, render.windowRenderState.height);
        float halfHeight = depth * heightPerDepth;
        float halfWidth = depth * widthPerDepth;
        var target = new Vector3f((2f * x - 1f) * halfWidth, (1f - 2f * frame.y) * halfHeight, -depth);
        var rotation = new Quaternionf().rotationZ((float) Math.toRadians(angle))
            .rotateY((float) Math.toRadians(frame.depthAngle)).rotateX(0.16f);
        if (draw >= 0f) {
            var start = DRAW[0];
            float startX = leftHanded ? 1f - start.x : start.x;
            var pivot = new Vector3f((2f * startX - 1f) * start.depth * widthPerDepth,
                (1f - 2f * start.y) * start.depth * heightPerDepth, -start.depth);
            float turn = (leftHanded ? -1f : 1f) * FORWARD_TURN * ease(swing / 0.24f);
            target.sub(pivot).rotateY(turn).add(pivot);
            // Bring the later stroke closer without shifting the grip across the viewport.
            target.mul(Mth.lerp(ease(swing / 0.65f), 1f, 0.72f));
            rotation.rotateLocalY(turn);
            target.y += target.z * heightPerDepth * 0.90f * recovery(draw);
        }
        if (draw < 0f && hold < 1f) {
            target = inView.transformPosition(new Vector3f(grip)).lerp(target, hold);
            rotation = inView.getUnnormalizedRotation(new Quaternionf()).normalize().slerp(rotation, hold);
        }
        inView.translationRotateScale(target, rotation, scale).translate(-grip.x, -grip.y, -grip.z);
        main.pose().set(new Matrix4f(view).invert().mul(inView));
        main.normal().rotation(main.pose().getUnnormalizedRotation(new Quaternionf()).normalize());
    }

    private static Frame sample(float progress) {
        int end = 1;
        while (end < DRAW.length - 1 && DRAW[end].time < progress) end++;
        var before = DRAW[Math.max(0, end - 2)];
        var from = DRAW[end - 1];
        var to = DRAW[end];
        var after = DRAW[Math.min(DRAW.length - 1, end + 1)];
        float t = Mth.clamp((progress - from.time) / (to.time - from.time), 0f, 1f);
        return new Frame(progress, Mth.catmullrom(t, before.x, from.x, to.x, after.x),
            Mth.catmullrom(t, before.y, from.y, to.y, after.y),
            Mth.catmullrom(t, before.depth, from.depth, to.depth, after.depth),
            Mth.catmullrom(t, before.angle, from.angle, to.angle, after.angle),
            Mth.catmullrom(t, before.depthAngle, from.depthAngle, to.depthAngle, after.depthAngle));
    }

    private record Frame(float time, float x, float y, float depth, float angle, float depthAngle) {}
}
