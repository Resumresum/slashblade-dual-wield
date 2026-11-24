package local.dualwield.probe;

import static local.dualwield.probe.RecipeProbe.require;

import java.util.List;
import local.dualwield.CombatGrowth;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

final class EquipmentProbe {
    private EquipmentProbe() {}

    static void run(ServerPlayer player, List<String> results) {
        var inventory = player.getInventory();
        int selected = inventory.getSelectedSlot(), other = (selected + 1) % 9;
        var oldMain = player.getMainHandItem();
        var oldOff = player.getOffhandItem();
        var oldOther = inventory.getItem(other);
        var main = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY)
            .getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        var off = main.copy();
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
            inventory.setItem(other, main.copy());
            var pair = CombatGrowth.capture(player);
            require(pair != null && pair.valid(), "equipment probe did not capture the equipped blades");
            inventory.setSelectedSlot(selected);
            inventory.setItem(selected, main);
            inventory.setItem(Inventory.SLOT_OFFHAND, off);
            CapabilitySlashBlade.getBladeState(off).orElseThrow().setProudSoulCount(123);
            require(pair.valid(), "same-slot selection, same-stack writes or routine blade state invalidated participation");

            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(other));
            player.connection.handleSetCarriedItem(new ServerboundSetCarriedItemPacket(selected));
            require(!pair.valid(), "same-tick hotbar packet away/back revived prior fight participation");

            pair = CombatGrowth.capture(player);
            inventory.setItem(Inventory.SLOT_OFFHAND, off.copy());
            inventory.setItem(Inventory.SLOT_OFFHAND, off);
            require(!pair.valid(), "same-tick inventory offhand replacement revived prior fight participation");

            pair = CombatGrowth.capture(player);
            ItemStack removed = inventory.removeItemNoUpdate(selected);
            inventory.setItem(selected, removed);
            require(!pair.valid(), "same-tick selected blade removal and restoration revived participation");
            results.add("PASS: real hotbar packets and direct inventory swaps/removals permanently invalidated old pairs; same-stack writes and blade-state updates preserved them");
        } finally {
            inventory.setSelectedSlot(selected);
            inventory.setItem(other, oldOther);
            player.setItemInHand(InteractionHand.MAIN_HAND, oldMain);
            player.setItemInHand(InteractionHand.OFF_HAND, oldOff);
        }
    }
}
