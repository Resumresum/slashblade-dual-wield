package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import local.dualwield.CombatGrowth;
import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.event.SlashBladeEvent;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;

/** Uses real mob damage/death/XP dispatch; every scenario completes in the calling server tick. */
final class GrowthProbe {
    private static ItemStack watched;
    private static int kills, souls, killEvents, soulEvents, bonus;
    private static boolean listening;

    private GrowthProbe() {}

    static void run(ServerPlayer player, List<String> results) throws ReflectiveOperationException {
        listen();
        var oldMain = player.getMainHandItem();
        var oldOff = player.getOffhandItem();
        var position = player.position();
        var velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var targets = new ArrayList<Zombie>();
        var template = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
            .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        Method sweep = Class.forName("local.dualwield.BladeSweep").getDeclaredMethod("offhandSweep", ServerPlayer.class);
        sweep.setAccessible(true);
        try {
            // Keep existing combat/catalog fixtures out of these real area attacks.
            player.setPos(position.x, position.y + 32, position.z);
            player.setYRot(0);
            player.setXRot(0);
            var main = clean(template);
            var off = clean(template);
            equip(player, main, off);
            var first = target(player, targets, 0.01f);
            sweep.invoke(null, player);
            require(!first.isAlive() && killEvents == 1 && soulEvents == 1,
                "growth probe did not traverse real death and XP events");
            require(units(main, false) == 7 && units(off, false) == 3,
                "first offhand killing hit did not retain 7/3 fractional kill credit");
            main = roundTrip(player, main);
            off = roundTrip(player, off);
            require(units(main, false) == 7 && units(off, false) == 3,
                "fractional kill credit was lost during blade NBT serialization");
            int savedSouls = souls;
            require(units(main, true) + units(off, true) == savedSouls * 10,
                "fractional proud souls were lost during blade NBT serialization");
            // Keep the reward totals across replacing stacks with their deserialized copies.
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            watched = main;
            for (int i = 1; i < 10; i++) {
                var target = target(player, targets, 0.01f);
                sweep.invoke(null, player);
                require(!target.isAlive(), "real offhand sweep failed to kill growth target " + i);
            }
            require(kills == 10 && killEvents == 10 && soulEvents == 10,
                "eligible kills or native reward events were duplicated");
            require(state(main).getKillCount() == 7 && state(off).getKillCount() == 3
                && CombatGrowth.remainder(main, false) == 0 && CombatGrowth.remainder(off, false) == 0,
                "ten eligible kills did not settle to seven main and three offhand");
            require(units(main, true) == souls * 7 && units(off, true) == souls * 3,
                "native XP/rank/cap reward was not conserved at 70/30 including stored tenths");
            results.add("PASS: real offhand lethal sweeps dispatched exactly ten death/XP rewards; kills settled 7/3 and proud souls conserved 70/30 through NBT serialization");

            main = clean(template);
            off = clean(template);
            equip(player, main, off);
            bonus = 2;
            var modified = target(player, targets, 0.01f);
            sweep.invoke(null, player);
            require(!modified.isAlive() && kills == 3 && units(main, false) == 21 && units(off, false) == 9
                && units(main, true) == souls * 7 && units(off, true) == souls * 3,
                "sharing did not preserve event-modified kill/proud-soul rewards");
            bonus = 0;
            int mainBefore = state(main).getProudSoulCount();
            int offBefore = state(off).getProudSoulCount();
            int mainRemainder = CombatGrowth.remainder(main, true), offRemainder = CombatGrowth.remainder(off, true);
            state(main).setProudSoulCount(mainBefore + 13);
            state(off).setProudSoulCount(offBefore - 1);
            require(state(main).getProudSoulCount() == mainBefore + 13 && state(off).getProudSoulCount() == offBefore - 1
                && CombatGrowth.remainder(main, true) == mainRemainder
                && CombatGrowth.remainder(off, true) == offRemainder,
                "non-combat soul changes or soul spending were split or erased fractional credit");
            results.add("PASS: event-modified rewards split once, while other proud-soul additions and spending retained native behavior");

            for (boolean pair : new boolean[]{false, true}) {
                main = clean(template);
                off = pair ? clean(template) : ItemStack.EMPTY;
                equip(player, main, off);
                kill(player, target(player, targets, 1));
                require(state(main).getKillCount() == 1 && CombatGrowth.remainder(main, false) == 0
                    && state(main).getProudSoulCount() == souls
                    && (!pair || units(off, false) == 0 && units(off, true) == 0),
                    "native solo/main-only reward was split without offhand participation");
            }
            results.add("PASS: single blade and idle equipped offhand kept full native main-hand death/XP credit");

            for (boolean restore : new boolean[]{false, true}) {
                main = clean(template);
                off = clean(template);
                equip(player, main, off);
                var fought = target(player, targets, 20);
                sweep.invoke(null, player);
                require(fought.isAlive() && fought.getHealth() < 20, "offhand failed to participate before replacement");
                var replacement = clean(template);
                player.setItemInHand(InteractionHand.OFF_HAND, replacement);
                if (restore) player.setItemInHand(InteractionHand.OFF_HAND, off);
                kill(player, fought);
                require(state(main).getKillCount() == 1 && units(off, false) == 0 && units(replacement, false) == 0
                    && state(main).getProudSoulCount() == souls,
                    "replacement or same-tick swap-away/back incorrectly retained fight participation");
            }
            main = clean(template);
            off = clean(template);
            equip(player, main, off);
            var immune = target(player, targets, 20);
            immune.setInvulnerable(true);
            sweep.invoke(null, player);
            require(immune.getHealth() == 20, "immune participation fixture was damaged");
            immune.setInvulnerable(false);
            kill(player, immune);
            require(state(main).getKillCount() == 1 && units(off, false) == 0,
                "rejected offhand damage qualified for growth sharing");
            results.add("PASS: replacing the offhand, swapping away/back in one tick, and rejected offhand damage did not grant shared growth");

            delayed(player, template, targets, false);
            delayed(player, template, targets, true);
            results.add("PASS: real offhand Judgement Cut retained participation through its delayed projectile; replacement blades received no delayed shared credit");
        } finally {
            watched = null;
            bonus = 0;
            for (var target : targets) target.discard();
            player.level().getEntitiesOfClass(EntityJudgementCut.class, player.getBoundingBox().inflate(24),
                cut -> cut.getShooter() == player).forEach(EntityJudgementCut::discard);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            player.setItemInHand(InteractionHand.OFF_HAND, oldOff);
            player.setPos(position);
            player.setDeltaMovement(velocity);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private static void delayed(ServerPlayer player, ItemStack template, List<Zombie> targets, boolean replace) {
        var main = clean(template);
        var off = clean(template);
        off.enchant(player.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.UNBREAKING), 1);
        state(off).setSlashArtsKey(Identifier.parse("slashblade:judgement_cut"));
        state(off).setProudSoulCount(200);
        equip(player, main, off);
        var previous = player.level().getEntitiesOfClass(EntityJudgementCut.class, player.getBoundingBox().inflate(24));
        require(HandSlashArts.cast(player, InteractionHand.OFF_HAND, 1, false), "delayed growth SA did not cast");
        var cut = player.level().getEntitiesOfClass(EntityJudgementCut.class, player.getBoundingBox().inflate(24),
            entity -> entity.getShooter() == player && !previous.contains(entity)).stream().findFirst().orElseThrow();
        var replacement = clean(template);
        if (replace) player.setItemInHand(InteractionHand.OFF_HAND, replacement);
        state(off).setProudSoulCount(0);
        var target = target(player, targets, 0.01f);
        cut.setPos(target.position());
        cut.setIsCritical(false);
        cut.tickCount = 2;
        cut.tick();
        require(!target.isAlive() && killEvents == 1 && soulEvents == 1, "delayed SA missed real death/XP path");
        require(replace ? state(main).getKillCount() == 1 && units(off, false) == 0 && units(replacement, false) == 0
            : units(main, false) == 7 && units(off, false) == 3,
            "delayed SA attributed shared growth to the wrong equipped pair");
        require(replace ? state(main).getProudSoulCount() == souls && units(replacement, true) == 0
            : units(main, true) == souls * 7 && units(off, true) == souls * 3,
            "delayed SA proud-soul credit did not preserve the original equipped pair");
        cut.discard();
    }

    private static Zombie target(ServerPlayer player, List<Zombie> targets, float health) {
        var zombie = EntityType.ZOMBIE.create(player.level(), EntitySpawnReason.COMMAND);
        require(zombie != null, "growth zombie could not be created");
        zombie.setNoAi(true);
        zombie.setSilent(true);
        zombie.setPos(player.getX(), player.getY(), player.getZ() + 3.5);
        zombie.setHealth(health);
        player.level().addFreshEntity(zombie);
        targets.add(zombie);
        return zombie;
    }

    private static void kill(ServerPlayer player, Zombie target) {
        target.invulnerableTime = 0;
        require(target.hurtServer(player.level(), player.damageSources().playerAttack(player), 1000)
            && !target.isAlive(), "main-hand fixture did not kill the growth zombie");
    }

    private static void equip(ServerPlayer player, ItemStack main, ItemStack off) {
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, off);
        watched = main;
        kills = souls = killEvents = soulEvents = 0;
    }

    private static ItemStack clean(ItemStack template) {
        var blade = template.copy();
        state(blade).setKillCount(0);
        state(blade).setProudSoulCount(0);
        return blade;
    }

    private static ItemStack roundTrip(ServerPlayer player, ItemStack blade) {
        var ops = RegistryOps.create(NbtOps.INSTANCE, player.registryAccess());
        var tag = ItemStack.CODEC.encodeStart(ops, blade).getOrThrow();
        return ItemStack.CODEC.parse(ops, tag).getOrThrow();
    }

    private static int units(ItemStack blade, boolean soul) {
        return (soul ? state(blade).getProudSoulCount() : state(blade).getKillCount()) * 10
            + CombatGrowth.remainder(blade, soul);
    }

    private static ISlashBladeState state(ItemStack blade) {
        return CapabilitySlashBlade.getBladeState(blade).orElseThrow();
    }

    private static void listen() {
        if (listening) return;
        listening = true;
        SlashBladeEvent.ADD_KILL_COUNT.register(event -> {
            if (event.getBlade() != watched) return;
            event.setNewCount(event.getNewCount() + bonus);
            kills += event.getNewCount();
            killEvents++;
        });
        SlashBladeEvent.ADD_PROUD_SOUL.register(event -> {
            if (event.getBlade() != watched) return;
            event.setNewCount(event.getNewCount() + bonus);
            souls += event.getNewCount();
            soulEvents++;
        });
    }
}
