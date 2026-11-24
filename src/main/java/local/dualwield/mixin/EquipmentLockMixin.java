package local.dualwield.mixin;

import local.dualwield.DualWieldCombat;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromBlockPacket;
import net.minecraft.network.protocol.game.ServerboundPickItemFromEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class EquipmentLockMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"), cancellable = true)
    private void dualwield$lockHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo callback) {
        if (!dualwield$locked()) return;
        player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        callback.cancel();
    }

    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void dualwield$preventSwapAndDrop(ServerboundPlayerActionPacket packet, CallbackInfo callback) {
        if (!dualwield$locked()) return;
        switch (packet.getAction()) {
            case SWAP_ITEM_WITH_OFFHAND, DROP_ITEM, DROP_ALL_ITEMS -> {
                player.containerMenu.sendAllDataToRemote();
                callback.cancel();
            }
            default -> {}
        }
    }

    @Inject(method = "handleContainerClick", at = @At("HEAD"), cancellable = true)
    private void dualwield$protectHeldSlots(ServerboundContainerClickPacket packet, CallbackInfo callback) {
        if (!dualwield$locked()) return;
        var menu = player.containerMenu;
        int selected = player.getInventory().getSelectedSlot();
        int slot = packet.slotNum();
        boolean touchesHand = false;
        if (slot >= 0 && slot < menu.slots.size()) {
            var target = menu.getSlot(slot);
            int targetSlot = target.getContainerSlot();
            touchesHand = target.container == player.getInventory()
                && (targetSlot == selected || targetSlot == Inventory.SLOT_OFFHAND);
        }
        if (touchesHand || packet.containerInput() == ContainerInput.SWAP
                && (packet.buttonNum() == selected || packet.buttonNum() == Inventory.SLOT_OFFHAND)) {
            menu.sendAllDataToRemote();
            callback.cancel();
        }
    }

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void dualwield$protectCreativeHeldSlots(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo callback) {
        if (dualwield$locked() && (packet.slotNum() == 36 + player.getInventory().getSelectedSlot() || packet.slotNum() == 45)) {
            player.inventoryMenu.sendAllDataToRemote();
            callback.cancel();
        }
    }

    @Inject(method = "handlePickItemFromBlock", at = @At("HEAD"), cancellable = true)
    private void dualwield$lockPickBlock(ServerboundPickItemFromBlockPacket packet, CallbackInfo callback) {
        if (dualwield$locked()) callback.cancel();
    }

    @Inject(method = "handlePickItemFromEntity", at = @At("HEAD"), cancellable = true)
    private void dualwield$lockPickEntity(ServerboundPickItemFromEntityPacket packet, CallbackInfo callback) {
        if (dualwield$locked()) callback.cancel();
    }

    @Unique
    private boolean dualwield$locked() {
        return player.level().getServer().isSameThread() && DualWieldCombat.active(player);
    }
}
