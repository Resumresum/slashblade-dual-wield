package local.dualwield;

import java.util.ArrayList;
import java.util.List;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Disposable, real-server regression. Call tick after the production END_SERVER_TICK listeners. */
public final class FeedbackServerProbe {
    private final ServerPlayer player;
    private final ItemStack shortBlade, longBlade;
    private final LivingEntity target;
    private final List<Long> hits = new ArrayList<>();
    private final List<String> results = new ArrayList<>();
    private int phase;
    private long start;
    private boolean finalContactWasActive;

    public FeedbackServerProbe(ServerPlayer player, ItemStack shortBlade, ItemStack longBlade, LivingEntity target) {
        this.player = player;
        this.shortBlade = shortBlade.copy();
        this.longBlade = longBlade.copy();
        this.target = target;
        require(BladeTypes.isWakizashi(this.shortBlade) && !BladeTypes.isWakizashi(this.longBlade), "wrong probe blades");
        CapabilitySlashBlade.getBladeState(this.longBlade).orElseThrow().setSlashArtsKey(Identifier.parse("slashblade:none"));
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
            if (phase > 0 && phase < 11 && entity == target && source.getEntity() == player && taken > 0 && !blocked) {
                hits.add(player.level().getGameTime() - start);
                if (phase == 6 && player.level().getGameTime() - start == 70) {
                    finalContactWasActive = DualWieldCombat.active(player);
                }
            }
        });
    }

    public boolean tick() {
        long now = player.level().getGameTime();
        if (phase == 0) {
            player.setItemInHand(InteractionHand.MAIN_HAND, shortBlade);
            player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            place(1.5);
            begin(1);
            WakizashiCombat.attack(player);
        }
        long elapsed = now - start;
        if (phase == 1) {
            expect(elapsed, 2, 6, 10);
            if (elapsed == 3 || elapsed == 7) {
                WakizashiCombat.attack(player);
                WakizashiCombat.attack(player); // Repeated early packets must coalesce into one buffered press.
            }
            if (elapsed == 16) {
                results.add("PASS: basic inputs +0/+3/+7 land +2/+6/+10; one-tick buffering keeps four-tick cadence and adds no fourth hit");
                begin(2);
                WakizashiCombat.onNativeDash(player);
                WakizashiCombat.attack(player);
            }
        } else if (phase == 2) {
            expect(elapsed, 1, 4, 7);
            if (elapsed == 10) {
                results.add("PASS: triple thrust contacts at +1/+4/+7");
                // Inventory hooks must invalidate even when the original stack returns before the next tick.
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, shortBlade);
                begin(3);
                WakizashiCombat.attack(player);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND, shortBlade);
            }
        } else if (phase == 3) {
            expect(elapsed);
            if (elapsed == 5) {
                results.add("PASS: swap-away-and-back before contact cancels pending damage");
                place(9);
                begin(4);
                WakizashiCombat.attack(player);
            }
        } else if (phase == 4) {
            expect(elapsed);
            if (elapsed == 5) {
                results.add("PASS: a whiff produces no target damage");
                player.setItemInHand(InteractionHand.MAIN_HAND, longBlade);
                player.setItemInHand(InteractionHand.OFF_HAND, longBlade.copy());
                place(1.5);
                begin(5);
                var state = CapabilitySlashBlade.getBladeState(longBlade).orElseThrow();
                state.setLastActionTime(now);
                state.setComboSeq(ComboStateRegistry.getId(ComboStateRegistry.COMBO_A1));
                FollowUp.hit(player, target, 4f);
                // Queue the passive hit without letting a native main-hand animation add another attack.
                state.setComboSeq(ComboStateRegistry.getId(ComboStateRegistry.NONE));
            }
        } else if (phase == 5) {
            expect(elapsed, 4);
            if (elapsed == 6) {
                results.add("PASS: passive follow-up contact remains main-hit +4");
                begin(6);
                DualWieldCombat.press(player, CombatPackets.ACTION);
                require(DualWieldCombat.active(player), "probe needs a player without an active burst cooldown");
            }
        } else if (phase == 6) {
            expect(elapsed, 2, 6, 10, 14, 18, 22, 26, 30, 34, 38, 42, 46, 54, 54, 58, 62, 66, 70);
            if(elapsed==5 || elapsed==17) DualWieldCombat.press(player,CombatPackets.ACTION);
            if (elapsed == 69) require(DualWieldCombat.active(player), "burst ended before the delayed final fallback");
            if (elapsed == 72) {
                require(finalContactWasActive && !DualWieldCombat.active(player), "burst cleanup lost final-contact context");
                results.add("PASS: twelve 4-tick real contacts, unchanged double/SA slots and final fallback context; repeated burst input adds no hits");
                player.setItemInHand(InteractionHand.MAIN_HAND, shortBlade);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                place(1.5);
                begin(7);
                WakizashiCombat.attack(player);
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
        } else if (phase == 7) {
            expect(elapsed);
            if (elapsed == 5) {
                player.stopUsingItem();
                results.add("PASS: starting charge before contact cancels the hidden basic hit");
                player.setItemInHand(InteractionHand.MAIN_HAND, shortBlade);
                player.setItemInHand(InteractionHand.OFF_HAND, longBlade.copy());
                place(1.5);
                begin(8);
                DualWieldCombat.press(player, CombatPackets.ACTION);
                WakizashiCombat.onNativeDash(player);
            }
        } else if (phase == 8) {
            expect(elapsed, 2);
            if (elapsed == 5) {
                results.add("PASS: a new short-blade dash does not cancel the separate offhand sweep");
                begin(9);
            }
        } else if (phase == 9) {
            expect(elapsed);
            if(elapsed==80) {
                player.setItemInHand(InteractionHand.MAIN_HAND,longBlade);
                player.setItemInHand(InteractionHand.OFF_HAND,longBlade.copy());
                place(1.5);
                begin(10);
                DualWieldCombat.press(player,CombatPackets.ACTION);
                require(DualWieldCombat.active(player),"second burst should start after cooldown");
            }
        } else if (phase == 10) {
            expect(elapsed);
            if(elapsed==1) {
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
                player.setItemInHand(InteractionHand.MAIN_HAND,longBlade);
            }
            if(elapsed==8) {
                require(!DualWieldCombat.active(player),"equipment change must end burst");
                results.add("PASS: swapping equipment during dense burst cancels pending contact and remaining stages");
                phase=11;
                return true;
            }
        }
        require(elapsed < 100, "server feedback probe stalled at phase " + phase + ": " + hits);
        return phase == 11;
    }

    public String results() { return String.join("\n", results); }

    private void begin(int next) {
        phase = next;
        start = player.level().getGameTime();
        hits.clear();
    }

    private void expect(long elapsed, long... contacts) {
        var expected = new ArrayList<Long>();
        for (long tick : contacts) if (tick <= elapsed) expected.add(tick);
        require(hits.equals(expected), "phase " + phase + " at +" + elapsed + ": expected " + expected + ", got " + hits);
    }

    private void place(double distance) {
        player.setYRot(0);
        player.setXRot(0);
        target.setPos(player.getX(), player.getY(), player.getZ() + distance);
        target.setDeltaMovement(Vec3.ZERO);
        target.invulnerableTime = 0;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
