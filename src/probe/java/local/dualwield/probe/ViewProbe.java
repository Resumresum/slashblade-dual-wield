package local.dualwield.probe;

import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import local.dualwield.BladeTypes;
import local.dualwield.client.CombatClient;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.client.renderer.special.BladeBaseRenderer;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.gamerules.GameRules;

final class ViewProbe {
    private static final List<CameraType> CAMERAS =
        List.of(CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT,
            CameraType.THIRD_PERSON_BACK, CameraType.FIRST_PERSON, CameraType.FIRST_PERSON);
    private static final List<String> VIEW_NAMES = List.of("back", "front", "side", "first", "first-action");
    private static final boolean CHECK_CATALOG = Boolean.getBoolean("dualwield.probe.combat");
    private final Path report;
    private final Path output = Path.of(System.getProperty("dualwield.probe.evidence"));
    private int age, phase, frames;
    private volatile boolean ready;
    private List<Sample> samples = List.of();
    private volatile ItemStack expectedOff;
    private boolean requested;
    private static final List<Integer> CHARGE_PITCHES = List.of(0, -25, 20, 40);
    private static final int CHARGE_VIEWS_PER_BLADE = CHARGE_PITCHES.size() + 1;
    private int chargeView;
    private boolean capturedChargeLift;
    private static final List<String> RELEASE_VIEWS = List.of("draw", "strike", "finish", "held");
    private static final List<Float> RELEASE_PHASES = List.of(0.08f, 0.26f, 0.60f, 0.77f);
    private int releaseViews;

    ViewProbe(Path report) {
        this.report = report;
    }

    boolean tick(Minecraft client) {
        if (client.player == null || client.level == null || client.screen != null) return false;
        try {
            if (++age > 1500) return finish("FAIL: view capture timed out at phase " + phase);
            if (phase == 10 + samples.size()) {
                if (chargeView < 2 * CHARGE_VIEWS_PER_BLADE) {
                    captureCharge(client);
                    return false;
                }
                if (releaseViews < RELEASE_VIEWS.size()) {
                    captureRelease(client);
                    return false;
                }
                for (String equipment : List.of("double-long", "long-wakizashi")) {
                    for (String view : VIEW_NAMES) {
                        if (!saved(equipment + "-" + view)) return false;
                    }
                }
                for (var sample : samples) {
                    if (!saved(sample.name())) return false;
                }
                for (String kind : List.of("long", "short")) {
                    if (!saved("charge-" + kind + "-idle") || !saved("charge-" + kind + "-ready")) return false;
                    for (int pitch : CHARGE_PITCHES.subList(1, CHARGE_PITCHES.size())) {
                        if (!saved("charge-" + kind + "-ready-" + pitch)) return false;
                    }
                }
                if (!saved("charge-short-lifting")) return false;
                for (String view : RELEASE_VIEWS) {
                    if (!saved("charge-short-" + view)) return false;
                }
                return finish("PASS: both combinations captured from front, back, side and first person\n"
                    + "Additional side views: " + samples.size() + "\nPASS: captured first-person charge, SA draw, arc and finish");
            }
            boolean catalog = phase >= 10;
            int view = catalog ? 2 : phase % 5;
            if (!requested) {
                requested = true;
                equip(client, phase < 5, catalog ? samples.get(phase - 10) : null);
            }
            if (!ready) return false;
            var off = client.player.getOffhandItem();
            if (!off.is(expectedOff.getItem()) || !off.getHoverName().equals(expectedOff.getHoverName())
                    || BladeTypes.isCraftedWakizashi(off) != BladeTypes.isCraftedWakizashi(expectedOff)) return false;
            boolean small = BladeTypes.isWakizashi(expectedOff);
            if (frames == 0) {
                client.options.setCameraType(CAMERAS.get(view));
                client.options.hideGui = view < 3;
            }
            float bodyYaw = client.player.getYRot() + (view == 2 ? 88 : 0);
            client.player.yBodyRotO = bodyYaw;
            client.player.setYBodyRot(bodyYaw);
            client.player.yHeadRotO = bodyYaw;
            client.player.setYHeadRot(bodyYaw);
            client.gui.getChat().clearMessages(false);
            client.getToastManager().clear();
            frames++;
            if (view == 4) {
                if (frames == 1) KeyMapping.click(InputConstants.getKey("key.keyboard.r"));
                int id = client.player.getId();
                if (small ? CombatClient.guardPose(id) < 0.85f : CombatClient.offhandSwingProgress(id) < 0.45f) {
                    return false;
                }
            } else if (frames < 9) {
                return false;
            }
            String name = catalog ? samples.get(phase - 10).name()
                : (small ? "long-wakizashi-" : "double-long-") + VIEW_NAMES.get(view);
            Screenshot.grab(output.toFile(), name + ".png",
                client.getMainRenderTarget(), 1, ignored -> {});
            phase++;
            frames = 0;
            if (catalog || phase % 5 == 0) {
                requested = false;
                ready = false;
            }
            return false;
        } catch (Exception error) {
            return finish("FAIL: " + error);
        }
    }

    private void captureCharge(Minecraft client) {
        boolean shortBlade = chargeView >= CHARGE_VIEWS_PER_BLADE;
        int view = chargeView % CHARGE_VIEWS_PER_BLADE;
        if (!requested) {
            requested = true;
            client.options.setCameraType(CameraType.FIRST_PERSON);
            client.options.hideGui = false;
            var server = client.getSingleplayerServer();
            server.execute(() -> {
                var player = server.getPlayerList().getPlayers().getFirst();
                player.stopUsingItem();
                var definition = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
                    .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE);
                var main = definition.getBlade();
                if (shortBlade) {
                    main = RecipeProbe.withBlade(player, main, new ItemStack(RecipeProbe.item("fired_mold")))
                        .getSlot(0).getItem().copy();
                }
                main.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                    .getOrThrow(Enchantments.POWER), 1);
                player.setItemInHand(InteractionHand.MAIN_HAND, main);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                ready = true;
            });
        }
        var main = client.player.getMainHandItem();
        if (!ready || !client.player.getOffhandItem().isEmpty() || !BladeTypes.isBlade(main)
                || BladeTypes.isCraftedWakizashi(main) != shortBlade) return;
        client.gui.getChat().clearMessages(false);
        client.getToastManager().clear();
        boolean held = view > 0;
        int pitch = held ? CHARGE_PITCHES.get(view - 1) : 0;
        client.player.setXRot(pitch);
        client.player.xRotO = pitch;
        if (++frames == 1 && view == 1) {
            client.options.keyUse.setDown(true);
            KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(1));
        }
        var blade = CapabilitySlashBlade.getBladeState(main).orElseThrow();
        int elapsed = client.player.getTicksUsingItem();
        if (shortBlade && view == 1 && !capturedChargeLift && elapsed >= 6 && !blade.isCharged(client.player)) {
            Screenshot.grab(output.toFile(), "charge-short-lifting.png", client.getMainRenderTarget(), 1, ignored -> {});
            capturedChargeLift = true;
        }
        if (shortBlade && view == 1 && elapsed == 13) {
            Screenshot.grab(output.toFile(), "charge-short-first-ready.png", client.getMainRenderTarget(), 1, ignored -> {});
        }
        if (frames < 9 || (held && (!blade.isCharged(client.player) || elapsed < 26))) return;
        String name = "charge-" + (shortBlade ? "short" : "long") + (held ? "-ready" : "-idle");
        if (pitch != 0) name += "-" + pitch;
        Screenshot.grab(output.toFile(), name + ".png", client.getMainRenderTarget(), 1, ignored -> {});
        chargeView++;
        frames = 0;
        if (!shortBlade && view == CHARGE_VIEWS_PER_BLADE - 1) {
            client.player.stopUsingItem();
            client.options.keyUse.setDown(false);
            requested = false;
            ready = false;
        }
    }

    private void captureRelease(Minecraft client) {
        client.player.setXRot(0);
        client.player.xRotO = 0;
        client.getToastManager().clear();
        if (++frames == 9) client.options.keyUse.setDown(false);
        float progress = CombatClient.shortArtDrawProgress();
        if (progress < RELEASE_PHASES.get(releaseViews)) return;
        String name = "charge-short-" + RELEASE_VIEWS.get(releaseViews++) + ".png";
        Screenshot.grab(output.toFile(), name, client.getMainRenderTarget(), 1, ignored -> {});
    }

    private boolean saved(String name) throws IOException {
        var path = output.resolve("screenshots").resolve(name + ".png");
        return Files.isRegularFile(path) && Files.size(path) > 0;
    }

    private void equip(Minecraft client, boolean small, Sample sample) {
        var server = client.getSingleplayerServer();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayers().getFirst();
            player.level().getGameRules().set(GameRules.SPAWN_MOBS, false, server);
            var definitions = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
            var definition = definitions.getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE);
            var main = definition.getBlade();
            var mold = new ItemStack(RecipeProbe.item("fired_mold"));
            var off = small ? RecipeProbe.withBlade(player, main, mold).getSlot(0).getItem().copy() : definition.getBlade();
            if (CHECK_CATALOG && phase == 0) {
                var distinct = new LinkedHashMap<Identifier, Sample>();
                for (var holder : definitions.listElements().sorted(Comparator.comparing(h -> h.key().identifier())).toList()) {
                    var blade = holder.value().getBlade();
                    if (!BladeTypes.isUsable(blade)) continue;
                    var model = BladeBaseRenderer.createBladeItemRenderState(blade).modelLocation;
                    var id = holder.key().identifier();
                    distinct.putIfAbsent(model, new Sample("off-" + id.toString().replace(':', '-'), main, blade));
                }
                var checks = new ArrayList<>(distinct.values());
                for (var sampleBlade : distinct.values()) {
                    String name = switch (sampleBlade.name()) {
                        case "off-foxextra-foxexfinal" -> "foxmoon";
                        case "off-slashblade_addon-fluorescent_bar" -> "fluorescent-bar";
                        default -> null;
                    };
                    if (name == null) continue;
                    var crafted = RecipeProbe.withBlade(player, sampleBlade.off(), mold).getSlot(0).getItem().copy();
                    checks.add(new Sample("off-" + name + "-crafted", main, crafted));
                    checks.add(new Sample("main-" + name, sampleBlade.off(), off));
                }
                samples = List.copyOf(checks);
            }
            if (sample != null) {
                main = sample.main().copy();
                off = sample.off().copy();
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            expectedOff = off.copy();
            ready = true;
        });
    }

    private boolean finish(String result) {
        try {
            Files.writeString(report, result);
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
        return true;
    }

    private record Sample(String name, ItemStack main, ItemStack off) {}
}
