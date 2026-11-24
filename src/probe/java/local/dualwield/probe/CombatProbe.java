package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import local.dualwield.BladeTypes;
import local.dualwield.BurstTargetHold;
import local.dualwield.CombatPackets;
import local.dualwield.CombatGrowth;
import local.dualwield.DualWieldCombat;
import local.dualwield.FollowUp;
import local.dualwield.arts.HandSlashArts;
import local.dualwield.client.CombatClient;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.entity.EntityDrive;
import mods.flammpfeil.slashblade.entity.EntityJudgementCut;
import mods.flammpfeil.slashblade.entity.EntitySlashEffect;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import mods.flammpfeil.slashblade.util.TargetSelector;
import mods.flammpfeil.slashblade.util.AttackManager;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

final class CombatProbe {
    private final ServerPlayer player;
    private final ItemStack main;
    private final ItemStack shortBlade;
    private final ItemStack longOffhand;
    private final List<String> results = new ArrayList<>();
    private final List<Long> strikeTicks = new ArrayList<>();
    private final long beginning;
    private final Zombie dummy;
    private final Zombie behind;
    private int hits, hitsBehind, clientAge;
    private int killsBefore, offhandKillsBefore;
    private int mainKillTenthsBefore, offhandKillTenthsBefore;
    private long burstStart = -1;
    private long guardStart;
    private long ordinaryStart;
    private int primaryHits, followHits;
    private float primaryDamage, followDamage;
    private long primaryImpactTick, followImpactTick;
    private int stage;
    private volatile boolean chordReady, guardReady;
    private volatile boolean posesReady, shortScreenshot, nativeReady, nativeCaptured;
    private volatile boolean followCueSeen;
    private volatile WakizashiProbe wakizashi;
    private boolean guardSent, guardScreenshot;

    CombatProbe(ServerPlayer player) {
        this.player = player;
        var definitions = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        var white = definitions.getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE);
        main = white.getBlade();
        shortBlade = RecipeProbe.withBlade(player, main, new ItemStack(RecipeProbe.item("fired_mold")))
            .getSlot(0).getItem().copy();
        longOffhand = white.getBlade();
        setArt(main, "twilighttsuki:firstaid", 0x55FF55);
        setArt(shortBlade, "twilighttsuki:firstaid", 0xFF66AA);
        setArt(longOffhand, "twilighttsuki:tfproudshield", 0x6688FF);
        player.setItemInHand(InteractionHand.MAIN_HAND, main);
        player.setItemInHand(InteractionHand.OFF_HAND, shortBlade);
        player.setYRot(0);
        player.setXRot(0);
        dummy = dummy(14);
        behind = dummy(-14);
        beginning = now();
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (source.getEntity() != player || blocked || taken <= 0) return;
            if (stage == 7 && entity == dummy) {
                if (FollowUp.suppressesKnockback(dummy)) {
                    followHits++;
                    followDamage = base;
                    followImpactTick = now();
                } else {
                    if (primaryHits == 0) {
                        primaryDamage = base;
                        primaryImpactTick = now();
                    }
                    primaryHits++;
                }
            }
            if (stage < 3 || !DualWieldCombat.active(player)) return;
            if (entity == dummy) {
                hits++;
                strikeTicks.add(now());
            }
            if (entity == behind) hitsBehind++;
        });
    }

    boolean tick() throws ReflectiveOperationException {
        long now = now();
        if (stage == 0) {
            require(now - beginning < 320, "client never became ready for the render checks");
            if (!posesReady) return false;
            player.setOnGround(true);
            player.setYRot(0);
            placeDummy(dummy, 1.95);
            placeDummy(behind, -1.95);
            dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            guardReady = true;
            stage = 8;
        } else if (stage == 8) {
            require(now - beginning < 360, "the client action key did not raise the wakizashi");
            if (!DualWieldCombat.guarding(player)) return false;
            guardStart = now;
            player.setHealth(10);
            player.invulnerableTime = 0;
            player.hurtServer(player.level(), player.damageSources().fall(), 3);
            require(player.getHealth() == 7 && DualWieldCombat.guarding(player),
                "fall damage consumed the guard or was reduced");
            player.invulnerableTime = 0;
            stage = 1;
        } else if (stage == 1 && now - guardStart >= 12) {
            float frontHealth = dummy.getHealth();
            float rearHealth = behind.getHealth();
            player.setHealth(7);
            player.hurtServer(player.level(), player.damageSources().mobAttack(behind), 10);
            require(Math.abs(player.getHealth() - 5) < 0.01, "guard did not reduce a rear hit by 80%: " + player.getHealth());
            require(dummy.getHealth() < frontHealth && behind.getHealth() < rearHealth, "counter did not sweep both sides");
            require(dummy.getDeltaMovement().z > 0.01, "counter did not push the front target outward");
            player.invulnerableTime = 0;
            require(!player.hurtServer(player.level(), player.damageSources().mobAttack(behind), 10),
                "second hit bypassed the counter immunity");
            require(state(shortBlade).getProudSoulCount() == 200 && state(main).getProudSoulCount() == 200,
                "counter charged a blade");
            results.add("PASS: one-key guard reduced a rear hit by 80%, counter hit both sides, no charge, second hit blocked");
            stage = 2;
        } else if (stage == 2 && now - guardStart >= 28) {
            require(shortScreenshot, "no first-person offhand attack cue reached the client");
            player.invulnerableTime = 0;
            player.setHealth(5);
            player.hurtServer(player.level(), player.damageSources().mobAttack(dummy), 1);
            require(Math.abs(player.getHealth() - 4) < 0.01, "counter immunity lasted longer than 16 ticks");
            player.setItemInHand(InteractionHand.OFF_HAND, longOffhand);
            player.setHealth(7);
            player.invulnerableTime = 0;
            player.setYRot(0);
            player.setXRot(0);
            placeDummy(dummy, 1.15);
            placeDummy(behind, -14);
            dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            dummy.setHealth(200);
            dummy.invulnerableTime = 0;
            ordinaryStart = now;
            state(main).updateComboSeq(player, ComboStateRegistry.getId(ComboStateRegistry.COMBO_A1));
            AttackManager.doSlash(player, 80f, true, false, 0.44f);
            stage = 7;
        } else if (stage == 7 && now - ordinaryStart >= 10) {
            require(primaryHits == 2 && followHits == 1, "ordinary multi-hit action had " + primaryHits
                + " main hits and " + followHits + " follow-ups; health=" + dummy.getHealth()
                + ", combo=" + state(main).getComboSeq() + ", reach=" + TargetSelector.getResolvedReach(player)
                + ", target distance=" + player.distanceTo(dummy) + ", slash entities="
                + player.level().getEntitiesOfClass(EntitySlashEffect.class, player.getBoundingBox().inflate(8)).size());
            require(primaryDamage > 0 && followDamage / primaryDamage > 0.70f && followDamage / primaryDamage < 0.80f,
                "unexpected follow-up damage: " + followDamage + " / " + primaryDamage);
            require(followImpactTick - primaryImpactTick == 4,
                "follow-up delay was " + (followImpactTick - primaryImpactTick) + " ticks instead of 4");
            if (!followCueSeen && now - ordinaryStart < 18) return false;
            require(followCueSeen, "the separate follow-up animation did not reach the client");
            require(state(longOffhand).getProudSoulCount() == 200 && longOffhand.getDamageValue() == 0,
                "passive follow-up charged the offhand weapon");
            results.add("PASS: native two-hit combo got one 75% follow-up four ticks after the first hit, with a dedicated offhand animation, no offhand charge or added knockback");
            placeDummy(dummy, 4.25);
            placeDummy(behind, -2.4);
            dummy.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0);
            chordReady = true;
            stage = 3;
        } else if (stage == 3) {
            require(now - guardStart < 140, "the client action key never initiated burst");
            if (DualWieldCombat.active(player)) {
                burstStart = now;
                stage = 4;
            }
        } else if (stage == 4) {
            long elapsed = now - burstStart;
            if (elapsed == 1) testLockAndImmunity();
            if (elapsed == 2) {
                require(BurstTargetHold.isHeld(dummy) && !BurstTargetHold.isHeld(behind),
                    "only the mob actually hit should be held");
                dummy.setDeltaMovement(0.35, 0.1, 0.25);
            }
            if (elapsed == 4) require(Math.abs(dummy.getX() - player.getX()) < 0.25,
                "held mob still moved sideways");
            if (elapsed == 57) {
                require(BurstTargetHold.isHeld(dummy), "held mob was released before SA");
                require(TargetSelector.getResolvedReach(player) >= 5, "burst SA melee reach was too short");
                require(player.getHealth() == player.getMaxHealth(), "first main First Aid did not restore health");
                player.setHealth(6);
            }
            if (elapsed == 61) {
                require(shieldCount() == 5, "first offhand shield did not reach five");
                setShields(1);
            }
            if (elapsed == 65) require(player.getHealth() == player.getMaxHealth(), "second main First Aid did not restore health");
            if (elapsed >= 69) {
                require(!DualWieldCombat.active(player), "burst did not release lock");
                require(shieldCount() == 5, "second shield did not restore five");
                require(state(main).getProudSoulCount() == 168, "main First Aid payment was not exactly twice");
                require(state(longOffhand).getProudSoulCount() == 200 && longOffhand.getDamageValue() == 0,
                    "offhand SA or swing charged resources");
                require(hits == 10 && hitsBehind == 0, "expected ten strikes in front only, got " + hits + "/" + hitsBehind);
                for (int strike = 1; strike < 8; strike++) {
                    require(strikeTicks.get(strike) - strikeTicks.get(strike - 1) == 6,
                        "alternating strike missed its six-tick beat: " + strikeTicks);
                }
                require(strikeTicks.get(8) - strikeTicks.get(7) == 10 && strikeTicks.get(9).equals(strikeTicks.get(8)),
                    "double slash did not land together after ten ticks: " + strikeTicks);
                Vec3 motion = player.getDeltaMovement();
                player.knockback(0.3, 1, 0);
                require(!motion.equals(player.getDeltaMovement()), "knockback stayed blocked after the final beat");
                int oldSlot = player.getInventory().getSelectedSlot();
                int nextSlot = (oldSlot + 1) % 9;
                player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(nextSlot));
                require(player.getInventory().getSelectedSlot() == nextSlot, "hotbar stayed locked after burst");
                player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(oldSlot));
                results.add("PASS: action key burst, ten hits beyond four blocks, only hit mob held through SA, equipment lock and immunity, two heals and two shields");
                results.add("PASS: first eight strikes were exactly six game ticks apart; the double slash hit twice on one tick");
                placeDummy(dummy, 1.95);
                dummy.clearFire();
                dummy.setHealth(1);
                dummy.invulnerableTime = 0;
                player.setYRot(0);
                killsBefore = state(main).getKillCount();
                offhandKillsBefore = state(longOffhand).getKillCount();
                mainKillTenthsBefore = CombatGrowth.remainder(main, false);
                offhandKillTenthsBefore = CombatGrowth.remainder(longOffhand, false);
                testOffhandAttack();
                results.add("PASS: offhand TwilightTsuki Crescent Slash used offhand color and reduced attack damage without charge");
                stage = 5;
            }
        } else if (stage == 5) {
            require(now - burstStart < 110, "offhand projectile did not reach the target");
            if (dummy.isAlive()) return false;
            require(state(main).getKillCount() * 10 + CombatGrowth.remainder(main, false)
                    == killsBefore * 10 + mainKillTenthsBefore + 7
                && state(longOffhand).getKillCount() * 10 + CombatGrowth.remainder(longOffhand, false)
                    == offhandKillsBefore * 10 + offhandKillTenthsBefore + 3, "offhand kill credited incorrectly: main "
                + killsBefore + " -> " + state(main).getKillCount() + ", offhand "
                + offhandKillsBefore + " -> " + state(longOffhand).getKillCount());
            results.add("PASS: delayed offhand Crescent Slash landed and its kill split 70/30 between the participating blades");
            testRepeatedJudgementCut();
            results.add("PASS: two fast Judgement Cuts from each hand created four independently colored and scaled cuts");
            var registry = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
            var kodachi = registry.getValueOrThrow(ResourceKey.create(SlashBladeDefinition.REGISTRY_KEY,
                Identifier.parse("twilighttsuki:sakuya"))).getBlade();
            require(BladeTypes.isWakizashi(kodachi) && !BladeTypes.isCraftedWakizashi(kodachi),
                "native kodachi lost its original short-blade classification");
            player.clearFire();
            setShields(0);
            player.setItemInHand(InteractionHand.OFF_HAND, kodachi);
            nativeReady = true;
            stage = 6;
        } else if (stage == 6) {
            require(now - burstStart < 170, "native kodachi was not observed in the client");
            if (nativeCaptured) {
                wakizashi = new WakizashiProbe(player, shortBlade.copy(), behind, results);
                stage = 9;
            }
        } else if (stage == 9 && wakizashi.tick()) {
            var pairTarget = dummy(12);
            PairActionProbe.run(player, shortBlade, longOffhand, pairTarget, behind, results);
            return true;
        }
        return false;
    }

    void clientTick(Minecraft client) {
        if (client.player == null || client.screen != null) return;
        clientAge++;
        if (!chordReady) {
            if (clientAge == 4) {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.options.hideGui = true;
            }
            if (clientAge == 10) screenshot(client, "short-back.png");
            if (clientAge == 11) client.options.keyUp.setDown(true);
            if (clientAge == 15) screenshot(client, "short-moving.png");
            if (clientAge == 16) {
                client.options.keyUp.setDown(false);
                client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            }
            if (clientAge == 21) screenshot(client, "short-front.png");
            if (clientAge == 22) {
                client.options.setCameraType(CameraType.FIRST_PERSON);
                client.options.hideGui = false;
            }
            if (clientAge == 25) posesReady = true;
            if (!guardSent && guardReady) {
                guardSent = true;
                KeyMapping.click(InputConstants.getKey("key.keyboard.r"));
            }
            if (!guardScreenshot && CombatClient.guardPose(client.player.getId()) > 0.8f) {
                guardScreenshot = true;
                screenshot(client, "wakizashi-guard-first-person.png");
            }
            if (client.options.getCameraType() == CameraType.FIRST_PERSON
                    && CombatClient.offhandSwingProgress(client.player.getId()) > 0.45f && !shortScreenshot) {
                shortScreenshot = true;
                screenshot(client, "short-first-person.png");
            }
            int entityId = client.player.getId();
            if (!followCueSeen && CombatClient.isFollowUpSwing(entityId)
                    && CombatClient.offhandSwingProgress(entityId) > 0.25f) {
                screenshot(client, "double-long-follow-up.png");
                followCueSeen = true;
            }
        }
        if (!sent && chordReady && BladeTypes.hasPair(client.player, false)) {
            if (chordTicks == 0) {
                client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                client.options.hideGui = true;
            }
            if (++chordTicks == 4) {
                screenshot(client, "double-long-back.png");
                sent = true;
                KeyMapping.click(InputConstants.getKey("key.keyboard.r"));
            }
        }
        if (nativeReady && !nativeCaptured && BladeTypes.isWakizashi(client.player.getOffhandItem())) {
            if (++nativeAge == 20) {
                screenshot(client, "native-kodachi-back.png");
                nativeCaptured = true;
            }
        }
        if (wakizashi != null) wakizashi.clientTick(client);
    }

    private boolean sent;
    private int chordTicks, nativeAge;

    private static void screenshot(Minecraft client, String name) {
        var target = new File(System.getProperty("dualwield.probe.evidence"));
        Screenshot.grab(target, name, client.getMainRenderTarget(), 1, message -> {});
    }

    private void testLockAndImmunity() {
        int selected = player.getInventory().getSelectedSlot();
        player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket((selected + 1) % 9));
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
        player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
            ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN));
        require(player.getMainHandItem() == main && player.getOffhandItem() == longOffhand
            && player.getInventory().getSelectedSlot() == selected, "equipment changed during burst");
        Vec3 motion = player.getDeltaMovement();
        player.knockback(0.3, 1, 0);
        require(motion.equals(player.getDeltaMovement()), "knockback moved the player during burst");
        require(!player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40)), "levitation landed during burst");
        float health = player.getHealth();
        player.hurtServer(player.level(), player.damageSources().magic(), 2);
        require(player.getHealth() < health && DualWieldCombat.active(player), "damage was blocked or broke the burst");
    }

    private void testOffhandAttack() {
        state(longOffhand).setSlashArtsKey(Identifier.parse("twilighttsuki:crescentslash"));
        var before = new ArrayList<>(player.level().getEntitiesOfClass(EntityDrive.class, player.getBoundingBox().inflate(6)));
        require(HandSlashArts.cast(player, InteractionHand.OFF_HAND, 1f, true), "full-strength test SA did not dispatch");
        var full = newDrive(before);
        before.add(full);
        require(HandSlashArts.cast(player, InteractionHand.OFF_HAND, 0.45f, true), "scaled test SA did not dispatch");
        var scaled = newDrive(before);
        int expected = state(longOffhand).getColorCode();
        require(full.getColor() == expected && scaled.getColor() == expected, "Crescent Slash used the wrong blade color");
        require(scaled.getDamage() > 0 && scaled.getDamage() < full.getDamage(), "offhand attack did not apply a damage scale");
        require(state(longOffhand).getProudSoulCount() == 200, "offhand attack charged its blade");
    }

    private EntityDrive newDrive(List<EntityDrive> before) {
        return player.level().getEntitiesOfClass(EntityDrive.class, player.getBoundingBox().inflate(6))
            .stream().filter(drive -> !before.contains(drive)).findFirst().orElseThrow();
    }

    private void testRepeatedJudgementCut() {
        var cutKey = Identifier.parse("slashblade:judgement_cut");
        state(main).setSlashArtsKey(cutKey);
        state(longOffhand).setSlashArtsKey(cutKey);
        for (int i = 0; i < 2; i++) {
            require(HandSlashArts.cast(player, InteractionHand.MAIN_HAND, 0.65f, false), "main Judgement Cut did not dispatch");
            require(HandSlashArts.cast(player, InteractionHand.OFF_HAND, 0.45f, true), "offhand Judgement Cut did not dispatch");
        }
        var cuts = player.level().getEntitiesOfClass(EntityJudgementCut.class, player.getBoundingBox().inflate(9));
        var primary = cuts.stream().filter(cut -> cut.getColor() == state(main).getColorCode()).toList();
        var secondary = cuts.stream().filter(cut -> cut.getColor() == state(longOffhand).getColorCode()).toList();
        require(primary.size() == 2 && secondary.size() == 2, "not all four Judgement Cuts spawned with their own blade colors");
        require(secondary.getFirst().getDamage() > 0
            && secondary.getFirst().getDamage() < primary.getFirst().getDamage(), "Judgement Cut damage scale was not applied");
    }

    private Zombie dummy(double z) {
        Zombie zombie = EntityType.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        zombie.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        zombie.setHealth(200);
        zombie.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        placeDummy(zombie, z);
        player.level().addFreshEntity(zombie);
        return zombie;
    }

    private void placeDummy(Zombie zombie, double z) {
        zombie.setPos(player.getX(), player.getY(), player.getZ() + z);
        zombie.setDeltaMovement(Vec3.ZERO);
    }

    private long now() { return player.level().getGameTime(); }

    private void setArt(ItemStack stack, String key, int color) {
        stack.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 1);
        var state = state(stack);
        state.setSlashArtsKey(Identifier.parse(key));
        state.setColorCode(color);
        state.setProudSoulCount(200);
    }

    private static mods.flammpfeil.slashblade.capability.slashblade.ISlashBladeState state(ItemStack stack) {
        return CapabilitySlashBlade.getBladeState(stack).orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private Object shield() throws ReflectiveOperationException {
        var type = (AttachmentType<Object>) Class.forName("twilightforest.init.TFDataAttachments")
            .getField("FORTIFICATION_SHIELDS").get(null);
        return ((AttachmentTarget) player).getAttachedOrCreate(type);
    }

    private int shieldCount() throws ReflectiveOperationException {
        var shield = shield();
        return (int) shield.getClass().getMethod("temporaryShieldsLeft").invoke(shield);
    }

    private void setShields(int count) throws ReflectiveOperationException {
        var shield = shield();
        shield.getClass().getMethod("setShields", net.minecraft.world.entity.LivingEntity.class, int.class, boolean.class)
            .invoke(shield, player, count, true);
    }

    String results() { return String.join("\n", results); }
}
