package local.dualwield.arts.mixin;

import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityDrive.class)
public abstract class DriveDamageMixin {
    @ModifyVariable(method = "setDamage", at = @At("HEAD"), argsOnly = true)
    private double dualwield$scaleDrive(double damage) {
        // TsukiDrive calculates absolute damage from its captured blade; the state mixin handles it.
        if (((Object) this).getClass().getName().equals("local.stage1.tsuki.TsukiDrive")) return damage;
        HandSlashArts.captureDelayed(this);
        return damage * HandSlashArts.damageMultiplier();
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"))
    private void dualwield$beginDriveHit(EntityHitResult hit, CallbackInfo callback) {
        HandSlashArts.beginDelayed(this);
    }

    @Inject(method = "onHitEntity", at = @At("RETURN"))
    private void dualwield$endDriveHit(EntityHitResult hit, CallbackInfo callback) {
        HandSlashArts.endDelayed(this);
    }
}
