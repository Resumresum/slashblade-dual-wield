package local.dualwield.mixin;

import local.dualwield.BladeTypes;
import mods.flammpfeil.slashblade.item.ItemSlashBlade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemSlashBlade.class, remap = false)
abstract class WakizashiNameMixin {
    @Inject(method = "getName", at = @At("HEAD"), cancellable = true)
    private void dualwield$showCraftedBladeName(ItemStack stack, CallbackInfoReturnable<Component> callback) {
        if (!BladeTypes.isCraftedWakizashi(stack)) return;
        // SlashBlade's getName normally bypasses the stored item-name component.
        var name = stack.get(DataComponents.ITEM_NAME);
        if (name != null && name.getContents() instanceof TranslatableContents translated
                && translated.getKey().equals("item.slashblade_dual_wield.wakizashi_from")) {
            callback.setReturnValue(name);
        }
    }
}
