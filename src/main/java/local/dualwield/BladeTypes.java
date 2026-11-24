package local.dualwield;

import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public final class BladeTypes {
    static final String WAKIZASHI_MARKER = "slashblade_dual_wield_wakizashi";

    private BladeTypes() {}

    public static boolean isBlade(ItemStack stack) {
        return CapabilitySlashBlade.getBladeState(stack).isPresent();
    }

    public static boolean isUsable(ItemStack stack) {
        return CapabilitySlashBlade.getBladeState(stack)
            .filter(state -> !state.isBroken() && !state.isSealed()).isPresent();
    }

    public static boolean isWakizashi(ItemStack stack) {
        var state = CapabilitySlashBlade.getBladeState(stack).orElse(null);
        if (state == null) return false;
        return isCraftedWakizashi(stack) || state.getModel().filter(model -> model.getNamespace().equals("twilighttsuki")
            && model.getPath().startsWith("model/twilighttsuki/kodachi/")).isPresent();
    }

    public static boolean isCraftedWakizashi(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBooleanOr(WAKIZASHI_MARKER, false);
    }

    public static boolean hasUsablePair(LivingEntity player) {
        return isUsable(player.getItemBySlot(EquipmentSlot.MAINHAND))
            && isUsable(player.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    public static boolean hasPair(LivingEntity player, boolean shortOffhand) {
        var main = player.getItemBySlot(EquipmentSlot.MAINHAND);
        var off = player.getItemBySlot(EquipmentSlot.OFFHAND);
        return isUsable(main) && !isWakizashi(main)
            && isUsable(off) && isWakizashi(off) == shortOffhand;
    }
}
