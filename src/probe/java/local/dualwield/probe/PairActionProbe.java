package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.util.List;
import local.dualwield.BurstTargetHold;
import local.dualwield.CombatPackets;
import local.dualwield.DualWieldCombat;
import local.dualwield.FollowUp;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

final class PairActionProbe {
    private PairActionProbe() {}

    static void run(ServerPlayer player, ItemStack shortBlade, ItemStack longBlade, Zombie front, Zombie behind,
            List<String> results) {
        var originalMain = player.getMainHandItem();
        var originalOffhand = player.getOffhandItem();
        float health = player.getHealth();
        int immunity = player.invulnerableTime;
        var main = shortBlade.copy();
        var offhand = longBlade.copy();
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        player.setYRot(0);
        player.setXRot(0);
        place(player, front, 3.8, 1.85);
        place(player, behind, 0, -1.9);
        var mainState = CapabilitySlashBlade.getBladeState(main).orElseThrow();
        var offhandState = CapabilitySlashBlade.getBladeState(offhand).orElseThrow();
        var mainCombo = ComboStateRegistry.getId(ComboStateRegistry.COMBO_A1);
        mainState.updateComboSeq(player, mainCombo);
        require(FollowUp.extraDamage(player, front, 1) == 0, "short main and long offhand got a passive follow-up");
        float frontHealth = front.getHealth();
        float rearHealth = behind.getHealth();
        int offhandSouls = offhandState.getProudSoulCount();
        int offhandDurability = offhand.getDamageValue();
        DualWieldCombat.press(player, CombatPackets.ACTION);
        require(front.getHealth() < frontHealth && behind.getHealth() == rearHealth,
            "short main and long offhand R missed the outer front fan or hit behind");
        require(!DualWieldCombat.active(player) && !DualWieldCombat.guarding(player) && !BurstTargetHold.isHeld(front),
            "short main and long offhand R started another action or held its target");
        require(mainState.getComboSeq().equals(mainCombo) && offhandState.getProudSoulCount() == offhandSouls
            && offhand.getDamageValue() == offhandDurability, "offhand sweep changed the main combo or charged the offhand");
        frontHealth = front.getHealth();
        DualWieldCombat.press(player, CombatPackets.ACTION);
        require(front.getHealth() == frontHealth, "short main and long offhand R lacked a cooldown");
        results.add("PASS: short main + long offhand R hit the outer front fan only, with cooldown and no hold or charge");

        player.setItemInHand(InteractionHand.OFF_HAND, shortBlade.copy());
        place(player, front, 0, 1.95);
        place(player, behind, 0, -1.95);
        frontHealth = front.getHealth();
        rearHealth = behind.getHealth();
        require(FollowUp.extraDamage(player, front, 1) == 0, "two short blades got a passive follow-up");
        DualWieldCombat.press(player, CombatPackets.ACTION);
        require(DualWieldCombat.guarding(player), "two short blades did not raise the guard");
        player.setHealth(10);
        player.invulnerableTime = 0;
        player.hurtServer(player.level(), player.damageSources().mobAttack(behind), 10);
        require(Math.abs(player.getHealth() - 8) < 0.01 && !DualWieldCombat.guarding(player),
            "two short blades did not reduce and consume a rear hit");
        require(front.getHealth() < frontHealth && behind.getHealth() < rearHealth,
            "two short blades did not counter in a ring");
        results.add("PASS: two short blades reused the rear-hit guard and ring counter");

        player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
        player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
        player.setHealth(health);
        player.invulnerableTime = immunity;
    }

    private static void place(ServerPlayer player, Zombie target, double x, double z) {
        target.setPos(player.getX() + x, player.getY(), player.getZ() + z);
        target.setDeltaMovement(Vec3.ZERO);
    }
}
