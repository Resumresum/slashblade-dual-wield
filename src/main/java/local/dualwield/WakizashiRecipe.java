package local.dualwield;

import java.util.UUID;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.init.SBItems;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

final class WakizashiRecipe extends CustomRecipe {
    private static final String WAKIZASHI = "item.slashblade_dual_wield.wakizashi";

    @Override
    public RecipeSerializer<WakizashiRecipe> getSerializer() {
        return DualWield.FORM_WAKIZASHI;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return sampleSlot(input) >= 0;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        int slot = sampleSlot(input);
        return slot < 0 ? ItemStack.EMPTY : fromSample(input.getItem(slot),
            input.items().stream().anyMatch(item -> item.is(DualWield.advancedMold)));
    }

    static ItemStack fromSample(ItemStack template) {
        return fromSample(template, false);
    }

    private static ItemStack fromSample(ItemStack template, boolean advanced) {
        var sample = CapabilitySlashBlade.getBladeState(template).orElseThrow();
        var result = new ItemStack(SBItems.SLASHBLADE);
        var state = CapabilitySlashBlade.getBladeState(result).orElseThrow();
        state.setTranslationKey(WAKIZASHI);
        sample.getModel().ifPresent(state::setModel);
        sample.getTexture().ifPresent(state::setTexture);
        state.setSlashArtsKey(sample.getSlashArtsKey());
        state.setColorCode(sample.getColorCode());
        if (advanced) {
            // Copy permanent settings explicitly: live combos, targets and reward fractions belong to the original.
            state.setTranslationKey(sample.getTranslationKey().isBlank()
                ? template.getItem().getDescriptionId() : sample.getTranslationKey());
            state.setBaseAttackModifier(sample.getBaseAttackModifier());
            state.setMaxDamage(sample.getMaxDamage());
            state.setComboRoot(sample.getComboRoot());
            state.setSpecialEffects(sample.getBladeState().getListOrEmpty("SpecialEffects").copy());
            state.setDefaultBewitched(sample.isDefaultBewitched());
            state.setCarryType(sample.getCarryType());
            state.setEffectColorInverse(sample.isEffectColorInverse());
            state.setAdjust(sample.getAdjust());
            state.setKillCount(sample.getKillCount());
            state.setRefine(sample.getRefine());
            state.setProudSoulCount(0);
            result.set(DataComponents.ENCHANTMENTS, template.getEnchantments());
            copyComponent(template, result, DataComponents.UNBREAKABLE);
            copyComponent(template, result, DataComponents.REPAIR_COST);
            copyComponent(template, result, DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            int damage = template.getMaxDamage() <= 0 ? 0 : (int) Math.round(
                (double) template.getDamageValue() / template.getMaxDamage() * result.getMaxDamage());
            result.setDamageValue(damage);
            var customName = template.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                result.set(DataComponents.CUSTOM_NAME, Component.translatable(
                    "item.slashblade_dual_wield.wakizashi_from", customName));
            }
            // Stage1 uses this UUID to reconnect a delayed Tsuki attack to its blade after a save.
            CustomData.update(DataComponents.CUSTOM_DATA, result,
                tag -> tag.putString("TsukiCapturedBlade", UUID.randomUUID().toString()));
        }
        result.set(DataComponents.ITEM_NAME, Component.translatable(
            "item.slashblade_dual_wield.wakizashi_from", template.getItem().getName(template)));
        CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(BladeTypes.WAKIZASHI_MARKER, true));
        return result;
    }

    private static <T> void copyComponent(ItemStack sample, ItemStack result,
            net.minecraft.core.component.DataComponentType<T> type) {
        var component = sample.get(type);
        if (component != null) result.set(type, component);
    }

    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        var remainders = CraftingRecipe.defaultCraftingReminder(input);
        int slot = sampleSlot(input);
        if (slot >= 0) remainders.set(slot, input.getItem(slot).copyWithCount(1));
        return remainders;
    }

    private static int sampleSlot(CraftingInput input) {
        if (input.ingredientCount() != 2) return -1;
        int sample = -1;
        boolean hasMold = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack item = input.getItem(i);
            if (item.isEmpty()) continue;
            if (item.is(DualWield.firedMold) || item.is(DualWield.advancedMold)) {
                hasMold = true;
                continue;
            }
            if (!BladeTypes.isUsable(item) || BladeTypes.isWakizashi(item)) return -1;
            sample = i;
        }
        return hasMold ? sample : -1;
    }

}
