package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import local.dualwield.WakizashiCombat;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Synchronous integration checks against the installed optional patch, without a Twilight compile dependency. */
final class BossCompatProbe {
    private static final List<String> MODES = List.of("short", "thrust", "sweep", "burst", "counter", "followup");

    private BossCompatProbe() {}

    static void run(ServerPlayer player, List<String> results) throws ReflectiveOperationException {
        try { Class.forName("local.sbcombat.CombatTargets"); }
        catch (ClassNotFoundException absent) {
            baseOnly(player, results);
            return;
        }
        var oldMain = player.getMainHandItem();
        var oldOff = player.getOffhandItem();
        var position = player.position();
        var velocity = player.getDeltaMovement();
        var gameMode = player.gameMode.getGameModeForPlayer();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var template = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
            .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        var shortBlade = RecipeProbe.withBlade(player, template, new ItemStack(RecipeProbe.item("fired_mold")))
            .getSlot(0).getItem().copy();
        var spawned = new ArrayList<LivingEntity>();
        try {
            player.setPos(position.x, position.y + 40, position.z);
            player.setGameMode(GameType.SURVIVAL);
            player.setYRot(0);
            player.setXRot(0);
            for (String mode : MODES) {
                for (String kind : List.of("wolf", "iron_golem", "bee")) {
                    equip(player, template, shortBlade, mode);
                    var hostile = spawn(player, "minecraft:" + kind, spawned);
                    ((Mob) hostile).setTarget(player);
                    float before = hostile.getHealth();
                    act(player, mode, hostile);
                    require(hostile.getHealth() < before, mode + " did not damage hostile " + kind);
                    discard(player, hostile);
                    // Quiet followup is a committed second hit; its initial native targeting is tested above.
                    if (mode.equals("followup")) continue;
                    equip(player, template, shortBlade, mode);
                    var friendly = spawn(player, "minecraft:" + kind, spawned);
                    before = friendly.getHealth();
                    act(player, mode, friendly);
                    require(friendly.getHealth() == before, mode + " damaged neutral " + kind);
                    discard(player, friendly);
                }
                results.add("PASS: " + mode + " damaged hostile wolves/golems/bees"
                    + (mode.equals("followup") ? " through managed quiet damage" : " and preserved neutral protection"));
            }
            for (String mode : MODES) {
                equip(player, template, shortBlade, mode);
                var open = hydra(player, spawned, "open");
                float before = open.getHealth();
                act(player, mode, open);
                float openDamage = before - open.getHealth();
                require(openDamage > 0, mode + " failed to damage Hydra's open physical head");
                discard(player, open);

                equip(player, template, shortBlade, mode);
                var overlap = hydra(player, spawned, "overlap");
                act(player, mode, overlap);
                Object[] heads = (Object[]) field(overlap, "hc");
                int damagedHeads = 0;
                for (int index = 0; index < 2; index++) {
                    if (((Number) heads[index].getClass().getMethod("getDamageTaken").invoke(heads[index])).floatValue() > 0) damagedHeads++;
                }
                require(damagedHeads == 1, mode + " multiplied damage across overlapping Hydra heads");
                discard(player, overlap);

                equip(player, template, shortBlade, mode);
                var immune = hydra(player, spawned, "open");
                immune.setInvulnerable(true);
                before = immune.getHealth();
                act(player, mode, immune);
                require(immune.getHealth() == before, mode + " bypassed Hydra's original invulnerability");
                discard(player, immune);

                if (!mode.equals("followup")) {
                    equip(player, template, shortBlade, mode);
                    var outside = hydra(player, spawned, "outside");
                    before = outside.getHealth();
                    act(player, mode, outside);
                    require(outside.getHealth() < before, mode + " missed reachable head outside parent query bounds");
                    discard(player, outside);
                    equip(player, template, shortBlade, mode);
                    var distant = hydra(player, spawned, "distant-part");
                    before = distant.getHealth();
                    act(player, mode, distant);
                    require(distant.getHealth() == before, mode + " hit distant parts merely because parent bounds overlapped");
                    discard(player, distant);
                }
                results.add("PASS: " + mode + " hit one open Hydra head, deduplicated overlapping heads and preserved invulnerability"
                    + (mode.equals("followup") ? "" : "; physical reach included outside-parent heads and rejected distant parts"));
            }
            equip(player, template, shortBlade, "counter");
            var armored = hydra(player, spawned, "closed");
            float before = armored.getHealth();
            act(player, "counter", armored);
            float actual = before - armored.getHealth();
            discard(player, armored);
            var control = hydra(player, spawned, "closed");
            before = control.getHealth();
            Entity head = head(control, 0);
            head.hurtServer(player.level(), player.damageSources().playerAttack(player), 2);
            require(actual == before - control.getHealth(), "custom ring changed native closed-head armor reduction");
            discard(player, control);
            equip(player, template, shortBlade, "sweep");
            var behind = hydra(player, spawned, "behind");
            before = behind.getHealth();
            act(player, "sweep", behind);
            require(behind.getHealth() == before, "front sweep used parent orientation instead of physical head fan");
            discard(player, behind);
            results.add("PASS: closed Hydra head armor matched direct physical-part control and front fan rejected a head behind the player");
        } finally {
            for (var entity : spawned) if (!entity.isRemoved()) discard(player, entity);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            player.setItemInHand(InteractionHand.OFF_HAND, oldOff);
            player.setPos(position);
            player.setDeltaMovement(velocity);
            player.setGameMode(gameMode);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private static void baseOnly(ServerPlayer player, List<String> results) throws ReflectiveOperationException {
        var oldMain = player.getMainHandItem();
        var oldOff = player.getOffhandItem();
        var position = player.position();
        var velocity = player.getDeltaMovement();
        float yaw = player.getYRot(), pitch = player.getXRot();
        var spawned = new ArrayList<LivingEntity>();
        var template = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
            .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        var shortBlade = RecipeProbe.withBlade(player, template, new ItemStack(RecipeProbe.item("fired_mold")))
            .getSlot(0).getItem().copy();
        try {
            player.setPos(position.x, position.y + 40, position.z);
            player.setYRot(0);
            player.setXRot(0);
            var zombie = spawn(player, "minecraft:zombie", spawned);
            equip(player, template, shortBlade, "short");
            float before = zombie.getHealth();
            act(player, "short", zombie);
            require(zombie.getHealth() < before, "base-only short attack failed without optional compatibility patch");
            equip(player, template, shortBlade, "sweep");
            zombie.invulnerableTime = 0;
            before = zombie.getHealth();
            act(player, "sweep", zombie);
            require(zombie.getHealth() < before, "base-only offhand sweep failed without optional compatibility patch");
            results.add("PASS: base-only short attack and offhand sweep damaged a real zombie without the optional combat compatibility patch");
        } finally {
            for (var entity : spawned) if (!entity.isRemoved()) discard(player, entity);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            player.setItemInHand(InteractionHand.OFF_HAND, oldOff);
            player.setPos(position);
            player.setDeltaMovement(velocity);
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    private static void equip(ServerPlayer player, ItemStack template, ItemStack shortBlade, String mode) {
        player.setItemInHand(InteractionHand.MAIN_HAND,
            (mode.equals("short") || mode.equals("thrust") ? shortBlade : template).copy());
        player.setItemInHand(InteractionHand.OFF_HAND, template.copy());
        CapabilitySlashBlade.getBladeState(player.getMainHandItem()).orElseThrow().setBroken(false);
        player.setOnGround(true);
        player.stopUsingItem();
    }

    private static void act(ServerPlayer player, String mode, LivingEntity target) throws ReflectiveOperationException {
        Class<?> sweep = Class.forName("local.dualwield.BladeSweep");
        Method method;
        switch (mode) {
            case "short", "thrust" -> {
                if (mode.equals("thrust")) WakizashiCombat.onNativeDash(player);
                method = WakizashiCombat.class.getDeclaredMethod("attack", ServerPlayer.class);
                method.setAccessible(true);
                method.invoke(null, player);
            }
            case "burst" -> {
                method = sweep.getDeclaredMethod("burst", ServerPlayer.class, InteractionHand.class, int.class);
                method.setAccessible(true);
                method.invoke(null, player, InteractionHand.OFF_HAND, 3);
            }
            case "followup" -> {
                method = Class.forName("local.dualwield.FollowUp").getDeclaredMethod("damageWithoutKnockback",
                    ServerPlayer.class, LivingEntity.class, float.class);
                method.setAccessible(true);
                method.invoke(null, player, target, 4f);
            }
            default -> {
                method = sweep.getDeclaredMethod(mode.equals("counter") ? "counter" : "offhandSweep", ServerPlayer.class);
                method.setAccessible(true);
                method.invoke(null, player);
            }
        }
    }

    private static LivingEntity spawn(ServerPlayer player, String id, List<LivingEntity> spawned) {
        var type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse(id));
        var entity = type.create(player.level(), EntitySpawnReason.COMMAND);
        require(entity instanceof LivingEntity, "missing boss-compat fixture " + id);
        var living = (LivingEntity) entity;
        if (living instanceof Mob mob) mob.setNoAi(true);
        living.setPos(player.getX(), player.getY(), player.getZ() + 1.9);
        player.level().addFreshEntity(living);
        spawned.add(living);
        return living;
    }

    private static LivingEntity hydra(ServerPlayer player, List<LivingEntity> spawned, String variant)
            throws ReflectiveOperationException {
        var hydra = spawn(player, "twilightforest:hydra", spawned);
        for (Object part : (Object[]) hydra.getClass().getMethod("getParts").invoke(hydra)) {
            ((Entity) part).setPos(player.getX() + 80, player.getY(), player.getZ() + 80);
        }
        Object[] heads = (Object[]) field(hydra, "hc");
        headState(heads[0], variant.equals("closed") ? "IDLE" : "FLAMING");
        head(hydra, 0).setPos(player.getX(), player.getY(), player.getZ() +
            (variant.equals("distant-part") ? 20 : variant.equals("behind") ? -4 : 1.9));
        if (variant.equals("outside")) hydra.setPos(player.getX(), player.getY(), player.getZ() + 14.5);
        if (variant.equals("overlap")) {
            headState(heads[1], "FLAMING");
            head(hydra, 1).setPos(head(hydra, 0).position());
        }
        Object map = Class.forName("carminite.interfaces.extensions.ILevelExtension")
            .getMethod("carminite$getPartEntityMap").invoke(player.level());
        if (((Map<?, ?>) map).get(head(hydra, 0).getId()) != head(hydra, 0)) {
            ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(hydra, player.level());
        }
        return hydra;
    }

    private static Entity head(LivingEntity hydra, int index) throws ReflectiveOperationException {
        return (Entity) field(((Object[]) field(hydra, "hc"))[index], "headEntity");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void headState(Object head, String name) throws ReflectiveOperationException {
        Field state = head.getClass().getDeclaredField("currentState");
        state.setAccessible(true);
        Object value = Enum.valueOf((Class) state.getType(), name);
        state.set(head, value);
        set(head, "prevState", value);
        set(head, "ticksNeeded", 10);
        set(head, "ticksProgress", 5);
        Object entity = field(head, "headEntity");
        entity.getClass().getMethod("setMouthOpen", float.class).invoke(entity, name.equals("FLAMING") ? 1f : 0f);
    }

    private static Object field(Object instance, String name) throws ReflectiveOperationException {
        var field = instance.getClass().getField(name);
        return field.get(instance);
    }

    private static void set(Object instance, String name, Object value) throws ReflectiveOperationException {
        var field = instance.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private static void discard(ServerPlayer player, LivingEntity entity) {
        ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(entity, player.level());
        entity.discard();
    }
}
