package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.util.ArrayList;
import java.util.List;
import local.dualwield.CombatPackets;
import local.dualwield.DualWieldCombat;
import local.dualwield.arts.HandSlashArts;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

/** Real server ticks, damage and action-key dispatch; no synthetic counter state. */
final class ParryArtProbe {
    private static final String[] CASES = {
        "First Aid", "unpressed expiry", "equipment change", "broken", "sealed", "unenchanted", "no art", "Judgement Cut"
    };
    private final ServerPlayer player;
    private final ItemStack originalMain, originalOffhand, mainTemplate, shortTemplate;
    private final float originalHealth;
    private final Zombie front, rear;
    private final List<String> results = new ArrayList<>();
    private final long beginning;
    private ItemStack main, offhand;
    private long counterAt;
    private int test = -1, artHits;
    private double ordinaryCutDamage;
    private boolean done, watchingArt;

    ParryArtProbe(ServerPlayer player) {
        this.player = player;
        originalMain = player.getMainHandItem();
        originalOffhand = player.getOffhandItem();
        originalHealth = player.getHealth();
        mainTemplate = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
            .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        mainTemplate.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.POWER), 1);
        shortTemplate = RecipeProbe.withBlade(player, mainTemplate, new ItemStack(RecipeProbe.item("fired_mold")))
            .getSlot(0).getItem().copy();
        shortTemplate.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(Enchantments.POWER), 1);
        front = dummy(18);
        rear = dummy(-18);
        beginning = now();
        require(!player.getAbilities().instabuild, "parry art payment requires the survival probe player");
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (!done && watchingArt && entity == front && source.getEntity() == player && taken > 0 && !blocked) artHits++;
        });
    }

    boolean tick() {
        if (done) return true;
        require(now() - beginning < 260, "parry art probe timed out at " + (test < 0 ? "initial drain" : CASES[test]));
        if (test < 0) {
            // PairActionProbe can finish on the same tick as a successful counter.
            if (now() - beginning < 17 || !player.connection.hasClientLoaded()
                    || player.isInvulnerableTo(player.level(), player.damageSources().mobAttack(rear))) return false;
            test = 0;
            beginCase();
            return false;
        }
        long age = now() - counterAt;
        if (test == 0) {
            if (age == 4) {
                require(player.getHealth() == 8, "counter automatically cast First Aid without a second R");
                unchangedPayment("unpressed counter");
                press();
                require(player.getHealth() == player.getMaxHealth(), "second R did not execute offhand First Aid");
                require(state(offhand).getProudSoulCount() == 184, "offhand First Aid did not pay its native 16 souls once");
                require(state(main).getProudSoulCount() == 200 && main.getDamageValue() == 0,
                    "offhand First Aid charged the main blade");
            }
            if (age >= 5 && age <= 15) {
                player.setHealth(8);
                press();
                require(player.getHealth() == 8 && state(offhand).getProudSoulCount() == 184
                    && !DualWieldCombat.guarding(player), "repeated R recast First Aid or rearmed the guard");
                immune("after spending the art opportunity");
            }
            if (age == 16) expired("after spending the art opportunity");
        } else if (test == 1) {
            if (age <= 15) {
                require(player.getHealth() == 8, "unpressed counter automatically healed");
                unchangedPayment("unpressed window");
            }
            if (age == 16) {
                press();
                require(player.getHealth() == 8 && DualWieldCombat.guarding(player),
                    "R at the expired window cast an art instead of starting a fresh guard");
                unchangedPayment("expired opportunity");
                // Change equipment on the next case so this unused fresh guard cannot leak into it.
            }
        } else if (test == 2) {
            if (age == 1) {
                int slot = player.getInventory().getSelectedSlot();
                player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket((slot + 1) % 9));
                player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(slot));
                immune("after same-tick hotbar away/back");
                press();
                require(player.getHealth() == 8 && !DualWieldCombat.guarding(player),
                    "same-tick hotbar away/back revived its art opportunity");
                unchangedPayment("invalidated equipment");
            }
            if (age == 15) immune("after equipment invalidation");
            if (age == 16) expired("after equipment invalidation");
        } else if (test >= 3 && test <= 6) {
            if (age == 1) {
                makeUnavailable();
                require(!HandSlashArts.canCast(player, InteractionHand.OFF_HAND), CASES[test] + " blade remained castable");
                press();
                require(player.getHealth() == 8, CASES[test] + " blade cast First Aid");
                unchangedPayment(CASES[test] + " failed cast");
                immune("with " + CASES[test] + " blade");
            }
            if (age == 2) {
                restoreAvailability();
                press();
                require(player.getHealth() == 8 && !DualWieldCombat.guarding(player),
                    CASES[test] + " failed cast left a reusable opportunity or rearmed guard");
                unchangedPayment(CASES[test] + " restored blade");
            }
            if (age == 15) immune("after " + CASES[test] + " failed cast");
            if (age == 16) expired("after " + CASES[test] + " failed cast");
        } else if (test == 7) {
            if (age == 4) {
                place(front, 4.5);
                front.setHealth(200);
                front.invulnerableTime = 0;
                state(offhand).setTargetEntityId(front);
                var before = cuts();
                watchingArt = true;
                press();
                var spawned = cuts().stream().filter(cut -> !before.contains(cut)).toList();
                require(spawned.size() == 1, "counter R did not dispatch exactly one native Judgement Cut");
                require(Math.abs(spawned.getFirst().getDamage() - ordinaryCutDamage) < 0.0001,
                    "counter art used a reduced damage multiplier: " + spawned.getFirst().getDamage() + "/" + ordinaryCutDamage);
                int cost = state(offhand).getSlashArts().getProudSoulCost();
                require(state(offhand).getProudSoulCount() == 200 - cost && offhand.getDamageValue() == 0,
                    "native offhand Judgement Cut payment was not exactly once at its normal cost");
                require(state(main).getProudSoulCount() == 200 && main.getDamageValue() == 0,
                    "native offhand Judgement Cut charged the main blade");
                press();
                require(cuts().size() == before.size() + 1 && !DualWieldCombat.guarding(player),
                    "another R duplicated Judgement Cut or rearmed guard");
            }
            if (age == 15) {
                require(artHits > 0 && front.getHealth() < 200, "counter-triggered Judgement Cut never damaged its real target");
                immune("after the attack art");
            }
            if (age == 16) expired("after the attack art");
        }
        if (age < 18) return false;
        results.add("PASS: counter optional offhand art - " + CASES[test]);
        if (++test < CASES.length) {
            beginCase();
            return false;
        }
        done = true;
        watchingArt = false;
        front.discard();
        rear.discard();
        player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
        player.setItemInHand(InteractionHand.OFF_HAND, originalOffhand);
        player.setHealth(originalHealth);
        player.invulnerableTime = 0;
        return true;
    }

    private void beginCase() {
        watchingArt = false;
        main = mainTemplate.copy();
        offhand = shortTemplate.copy();
        for (var stack : List.of(main, offhand)) {
            state(stack).setProudSoulCount(200);
            state(stack).setComboSeq(ComboStateRegistry.getId(ComboStateRegistry.NONE));
            state(stack).setSlashArtsKey(Identifier.parse("twilighttsuki:firstaid"));
        }
        state(offhand).setColorCode(0x19ABCC);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, offhand);
        player.stopUsingItem();
        player.setYRot(0);
        player.setXRot(0);
        player.setDeltaMovement(Vec3.ZERO);
        if (test == 7) {
            state(offhand).setSlashArtsKey(Identifier.parse("slashblade:judgement_cut"));
            var before = cuts();
            require(HandSlashArts.cast(player, InteractionHand.OFF_HAND, 1f, true), "ordinary native damage reference did not cast");
            var reference = cuts().stream().filter(cut -> !before.contains(cut)).findFirst().orElseThrow();
            ordinaryCutDamage = reference.getDamage();
            reference.discard();
            state(offhand).setComboSeq(ComboStateRegistry.getId(ComboStateRegistry.NONE));
        }
        place(front, 1.95);
        place(rear, -1.95);
        front.setHealth(200);
        rear.setHealth(200);
        front.invulnerableTime = rear.invulnerableTime = 0;
        press();
        require(DualWieldCombat.guarding(player), CASES[test] + " did not raise the guard through R");
        player.setHealth(10);
        player.invulnerableTime = 0;
        require(player.hurtServer(player.level(), player.damageSources().mobAttack(rear), 10),
            CASES[test] + " incoming mob hit was rejected before guard damage");
        require(Math.abs(player.getHealth() - 8) < 0.01 && !DualWieldCombat.guarding(player),
            CASES[test] + " counter lost 80% mitigation or did not consume guard: " + player.getHealth());
        require(front.getHealth() < 200 && rear.getHealth() < 200, CASES[test] + " counter lost its surrounding ring hit");
        unchangedPayment("successful counter");
        counterAt = now();
        immune("immediately after counter");
        place(front, 18);
        place(rear, -18);
    }

    private void makeUnavailable() {
        switch (test) {
            case 3 -> state(offhand).setBroken(true);
            case 4 -> state(offhand).setSealed(true);
            case 5 -> offhand.set(DataComponents.ENCHANTMENTS,
                net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
            case 6 -> state(offhand).setSlashArtsKey(Identifier.parse("slashblade:none"));
            default -> throw new AssertionError("not an unavailable-art case");
        }
    }

    private void restoreAvailability() {
        state(offhand).setBroken(false);
        state(offhand).setSealed(false);
        if (test == 5) offhand.set(DataComponents.ENCHANTMENTS, shortTemplate.getEnchantments());
        state(offhand).setSlashArtsKey(Identifier.parse("twilighttsuki:firstaid"));
        require(HandSlashArts.canCast(player, InteractionHand.OFF_HAND),
            "failed to restore " + CASES[test] + " blade: " + state(offhand).getBladeState()
                + ", enchantments=" + offhand.getEnchantments());
    }

    private void immune(String when) {
        player.invulnerableTime = 0;
        float health = player.getHealth();
        require(!player.hurtServer(player.level(), player.damageSources().mobAttack(rear), 1)
            && player.getHealth() == health, "16-tick counter immunity failed " + when);
    }

    private void expired(String when) {
        player.invulnerableTime = 0;
        player.setHealth(8);
        require(player.hurtServer(player.level(), player.damageSources().mobAttack(rear), 1)
            && Math.abs(player.getHealth() - 7) < 0.01, "counter immunity exceeded 16 ticks " + when);
    }

    private void unchangedPayment(String when) {
        require(state(main).getProudSoulCount() == 200 && state(offhand).getProudSoulCount() == 200
            && main.getDamageValue() == 0 && offhand.getDamageValue() == 0, "unexpected blade payment during " + when);
    }

    private List<EntityJudgementCut> cuts() {
        return player.level().getEntitiesOfClass(EntityJudgementCut.class, player.getBoundingBox().inflate(24))
            .stream().filter(cut -> cut.getColor() == state(offhand).getColorCode()).toList();
    }

    private Zombie dummy(double z) {
        Zombie zombie = EntityType.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        zombie.setHealth(200);
        place(zombie, z);
        player.level().addFreshEntity(zombie);
        return zombie;
    }

    private void place(Zombie zombie, double z) {
        zombie.setPos(player.getX(), player.getY(), player.getZ() + z);
        zombie.setDeltaMovement(Vec3.ZERO);
    }

    private void press() { DualWieldCombat.press(player, CombatPackets.ACTION); }
    private long now() { return player.level().getGameTime(); }
    private static ISlashBladeState state(ItemStack stack) { return CapabilitySlashBlade.getBladeState(stack).orElseThrow(); }
    String results() { return String.join("\n", results); }
}
