package local.dualwield.mixin;

import java.util.ArrayDeque;
import local.dualwield.CombatGrowth;
import local.dualwield.DualWieldCombat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Inventory mutations bypass LivingEntity.onEquipItem, including ordinary hotbar packets. */
@Mixin(Inventory.class)
abstract class InventoryEquipmentMixin {
    @Shadow public Player player;
    @Unique private final ArrayDeque<HeldItems> dualwield$hands = new ArrayDeque<>();

    @Inject(method = {"setSelectedSlot", "setItem", "pickSlot", "addAndPickItem", "clearContent", "dropAll",
        "replaceWith", "removeItem(Lnet/minecraft/world/item/ItemStack;)V"}, at = @At("HEAD"))
    private void dualwield$beforeChange(CallbackInfo callback) { dualwield$capture(); }

    @Inject(method = {"setSelectedSlot", "setItem", "pickSlot", "addAndPickItem", "clearContent", "dropAll",
        "replaceWith", "removeItem(Lnet/minecraft/world/item/ItemStack;)V"}, at = @At("RETURN"))
    private void dualwield$afterChange(CallbackInfo callback) { dualwield$invalidateChangedHands(); }

    @Inject(method = {"setSelectedItem", "removeItem(II)Lnet/minecraft/world/item/ItemStack;", "removeItemNoUpdate",
        "removeFromSelected", "add", "clearOrCountMatchingItems"}, at = @At("HEAD"))
    private void dualwield$beforeReturningChange(CallbackInfoReturnable<?> callback) { dualwield$capture(); }

    @Inject(method = {"setSelectedItem", "removeItem(II)Lnet/minecraft/world/item/ItemStack;", "removeItemNoUpdate",
        "removeFromSelected", "add", "clearOrCountMatchingItems"}, at = @At("RETURN"))
    private void dualwield$afterReturningChange(CallbackInfoReturnable<?> callback) { dualwield$invalidateChangedHands(); }

    @Unique
    private void dualwield$capture() {
        if (!(player instanceof ServerPlayer)) return;
        var main = player.getItemBySlot(EquipmentSlot.MAINHAND);
        var off = player.getItemBySlot(EquipmentSlot.OFFHAND);
        // Removal can empty the original stack in place, so retain its earlier emptiness as well.
        dualwield$hands.push(new HeldItems(main, off, main.isEmpty(), off.isEmpty()));
    }

    @Unique
    private void dualwield$invalidateChangedHands() {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        var before = dualwield$hands.pop();
        var main = player.getItemBySlot(EquipmentSlot.MAINHAND);
        var off = player.getItemBySlot(EquipmentSlot.OFFHAND);
        if (before.main() != main || before.off() != off
                || before.mainEmpty() != main.isEmpty() || before.offEmpty() != off.isEmpty()) {
            CombatGrowth.equipmentChanged(serverPlayer);
            DualWieldCombat.equipmentChanged(serverPlayer);
        }
    }

    public record HeldItems(ItemStack main, ItemStack off, boolean mainEmpty, boolean offEmpty) {}
}
