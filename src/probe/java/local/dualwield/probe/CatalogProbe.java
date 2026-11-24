package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import local.dualwield.BladeTypes;
import local.dualwield.CombatPackets;
import local.dualwield.DualWieldCombat;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.capability.mobeffect.CapabilityMobEffect;
import mods.flammpfeil.slashblade.client.renderer.model.BladeModelManager;
import mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.Minecraft;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;

final class CatalogProbe {
    private final List<Row> rows = new ArrayList<>();
    private volatile boolean assetsReady;
    private volatile RuntimeException assetFailure;
    private List<Row> usable;
    private ItemStack originalMain, originalOff;
    private Zombie liveTarget;
    private long nextHitTime;
    private int countersChecked;
    private int blockedAttempts;

    static CatalogProbe checkRecipes(ServerPlayer player) {
        var probe = new CatalogProbe();
        var definitions = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse("slashblade_dual_wield:dual_wield"));
        var creative = List.copyOf(tab.getDisplayItems());
        var mold = new ItemStack(RecipeProbe.item("fired_mold"));
        var originalMain = player.getMainHandItem();
        var originalOff = player.getOffhandItem();
        var attacker = EntityType.HUSK.create(player.level(), EntitySpawnReason.COMMAND);
        require(attacker != null, "unable to create a mock melee attacker");
        player.setItemInHand(InteractionHand.MAIN_HAND, definitions.getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade());
        try {
            for (var item : List.of(SBItems.SLASHBLADE_WOOD, SBItems.SLASHBLADE_BAMBOO,
                    SBItems.SLASHBLADE_SILVERBAMBOO, SBItems.SLASHBLADE_WHITE, SBItems.SLASHBLADE)) {
                probe.check(player, attacker, BuiltInRegistries.ITEM.getKey(item), new ItemStack(item), mold, creative);
            }
            for (var holder : definitions.listElements().sorted(java.util.Comparator.comparing(h -> h.key().identifier())).toList()) {
                probe.check(player, attacker, holder.key().identifier(), holder.value().getBlade(), mold, creative);
            }
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
        }
        return probe;
    }

    private void check(ServerPlayer player, LivingEntity attacker, Identifier id, ItemStack sample, ItemStack mold,
            List<ItemStack> creative) {
        var workbench = RecipeProbe.withBlade(player, sample, mold);
        var preview = workbench.getSlot(0).getItem();
        if (!BladeTypes.isUsable(sample)) {
            require(preview.isEmpty(), id + " invalid template unexpectedly crafted");
            var state = CapabilitySlashBlade.getBladeState(sample).orElse(null);
            String reason = state == null ? "non-blade" : state.isSealed() ? "sealed" : "broken";
            rows.add(new Row(id, sample, reason, null));
            return;
        }

        ItemStack blade;
        String kind;
        if (BladeTypes.isWakizashi(sample)) {
            require(preview.isEmpty(), id + " original short blade was resized again");
            kind = "native-kodachi";
            blade = sample;
        } else {
            require(!preview.isEmpty(), id + " has no recipe output");
            kind = "crafted-70-percent";
            blade = workbench.getSlot(0).remove(1);
            RecipeProbe.checkProduct(blade, sample);
            workbench.getSlot(0).onTake(player, blade);
            require(ItemStack.isSameItemSameComponents(sample, workbench.getSlot(4).getItem()), id + " sample changed");
            require(workbench.getSlot(9).getItem().isEmpty(), id + " mold not consumed");
            require(BladeTypes.isCraftedWakizashi(blade), id + " missing scale marker");
        }
        require(creative.stream().anyMatch(entry -> ItemStack.isSameItemSameComponents(entry, blade)),
            id + " was not discoverable in the dual-wield tab");
        player.setItemInHand(InteractionHand.OFF_HAND, blade.copy());
        require(BladeTypes.hasPair(player, true), id + " did not equip as a short blade");
        DualWieldCombat.press(player, CombatPackets.ACTION);
        require(DualWieldCombat.guarding(player), id + " did not enable guard");
        require(DualWieldCombat.guardDamage(player, player.damageSources().mobAttack(attacker), 10) == 2,
            id + " did not reduce incoming melee damage by 80%");
        require(DualWieldCombat.guardDamage(player, player.damageSources().playerAttack(player), 10) == 10,
            id + " incorrectly reduced self damage");
        rows.add(new Row(id, sample, kind, blade));
    }

    void checkClientAssets(Minecraft client) {
        if (assetsReady || assetFailure != null || client.level == null || client.player == null) return;
        try {
            var resourceManager = client.getResourceManager();
            var models = BladeModelManager.getInstance();
            for (var row : rows) {
                checkArtTooltip(client, row.sample, row.id);
                if (row.blade == null) continue;
                var render = BladeBaseRenderer.createBladeItemRenderState(row.blade);
                require(resourceManager.getResource(render.modelLocation).isPresent(), row.id + " missing model " + render.modelLocation);
                require(resourceManager.getResource(render.textureLocation).isPresent(), row.id + " missing texture " + render.textureLocation);
                require(models.getModel(render.modelLocation) != models.defaultModel, row.id + " model parser used fallback");
                checkArtTooltip(client, row.blade, row.id);
            }
            assetsReady = true;
        } catch (RuntimeException | AssertionError error) {
            assetFailure = new IllegalStateException("Wakizashi client asset check failed", error);
        }
    }

    private static void checkArtTooltip(Minecraft client, ItemStack blade, Identifier id) {
        var types = SwordType.from(blade);
        var lines = blade.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL);
        long arts = lines.stream().filter(line -> line.getContents() instanceof TranslatableContents text
            && text.getKey().equals("slashblade.tooltip.slash_art")).count();
        require(arts == (types.contains(SwordType.BEWITCHED) && !types.contains(SwordType.SEALED) ? 1 : 0),
            id + " did not follow SlashBlade's native SA tooltip rule");
    }

    boolean assetsReady() {
        if (assetFailure != null) throw assetFailure;
        return assetsReady;
    }

    boolean checkLiveCounters(ServerPlayer player) {
        var level = player.level();
        long now = level.getGameTime();
        if (usable == null) {
            usable = rows.stream().filter(row -> row.blade != null).toList();
            originalMain = player.getMainHandItem();
            originalOff = player.getOffhandItem();
            var definitions = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
            player.setItemInHand(InteractionHand.MAIN_HAND,
                definitions.getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade());
            liveTarget = EntityType.HUSK.create(level, EntitySpawnReason.COMMAND);
            require(liveTarget != null, "live counter target was unavailable");
            liveTarget.setNoAi(true);
            liveTarget.setPos(player.getX() + 1.8, player.getY(), player.getZ());
            level.addFreshEntity(liveTarget);
            // Let the integrated client acknowledge the initial spawn before applying damage.
            nextHitTime = now + 30;
        }
        if (now < nextHitTime) return false;
        if (countersChecked == usable.size()) {
            liveTarget.discard();
            player.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
            player.setItemInHand(InteractionHand.OFF_HAND, originalOff);
            player.setHealth(player.getMaxHealth());
            player.invulnerableTime = 0;
            return true;
        }

        var row = usable.get(countersChecked);
        var melee = player.damageSources().mobAttack(liveTarget);
        if (player.isInvulnerableTo(level, melee) || DualWieldCombat.counterInvulnerable(player, melee)
                || CapabilityMobEffect.MOB_EFFECT.maybeGet(player).flatMap(state -> state.getUntouchableTimeOut())
                    .filter(until -> now < until).isPresent()) {
            return false;
        }
        var blade = row.blade.copy();
        player.setItemInHand(InteractionHand.OFF_HAND, blade);
        liveTarget.setPos(player.getX() + 1.8, player.getY(), player.getZ());
        liveTarget.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        liveTarget.setHealth(20);
        liveTarget.invulnerableTime = 0;
        player.setHealth(20);
        player.invulnerableTime = 0;
        DualWieldCombat.press(player, CombatPackets.ACTION);
        require(DualWieldCombat.guarding(player), row.id + " live guard did not arm");
        if (!player.hurtServer(level, melee, 10)) {
            require(player.getHealth() == 20 && DualWieldCombat.guarding(player) && ++blockedAttempts < 3,
                row.id + " repeatedly blocked the synthetic hit before the counter");
            nextHitTime = now + 20;
            return false;
        }
        require(Math.abs(player.getHealth() - 18f) < 0.01, row.id + " live hit did not reduce to 2 damage");
        require(liveTarget.getHealth() < 20 && !DualWieldCombat.guarding(player), row.id + " ring counter did not fire");
        require(DualWieldCombat.counterInvulnerable(player, melee), row.id + " did not grant counter invulnerability");
        require(ItemStack.isSameItemSameComponents(blade, row.blade), row.id + " counter charged or changed the offhand");
        blockedAttempts = 0;
        countersChecked++;
        if (countersChecked % 20 == 0 || countersChecked == usable.size()) {
            System.out.println("[wakizashi-catalog] Verified " + countersChecked + "/" + usable.size() + " live counters");
        }
        nextHitTime = now + 16;
        return false;
    }

    String writeReport() throws IOException {
        var output = new ArrayList<String>();
        output.add("id,template,status,model,texture,sa,live_counter");
        for (var row : rows) {
            var state = CapabilitySlashBlade.getBladeState(row.blade == null ? row.sample : row.blade);
            output.add(String.join(",", row.id.toString(), quote(row.sample.getHoverName().getString()), row.kind,
                state.flatMap(s -> s.getModel()).map(Object::toString).orElse(""),
                state.flatMap(s -> s.getTexture()).map(Object::toString).orElse(""),
                state.map(s -> s.getSlashArtsKey()).map(Object::toString).orElse(""),
                row.blade != null && usable != null && countersChecked == usable.size() ? "pass" : ""));
        }
        Files.write(Path.of(System.getProperty("dualwield.probe.evidence"), "wakizashi-catalog.csv"), output);
        long crafted = rows.stream().filter(row -> row.kind.equals("crafted-70-percent")).count();
        long nativeShort = rows.stream().filter(row -> row.kind.equals("native-kodachi")).count();
        return "PASS: " + crafted + " crafted wakizashi + " + nativeShort
            + " native kodachi armed the guard and loaded model and texture; "
            + (rows.size() - crafted - nativeShort) + " ineligible entries rejected\n"
            + (usable == null ? "Live counters skipped" : "PASS: " + countersChecked + " live guard hits and ring counters; no offhand charge")
            + "\nPASS: base blades and shorts followed the native SA tooltip rules, including sealed blades";
    }

    private static String quote(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private record Row(Identifier id, ItemStack sample, String kind, ItemStack blade) {}
}
