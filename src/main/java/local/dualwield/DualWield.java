package local.dualwield;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mods.flammpfeil.slashblade.init.SBItems;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

public final class DualWield implements ModInitializer {
    static final String ID = "slashblade_dual_wield";
    static Item firedMold;
    static Item advancedMold;
    static final RecipeSerializer<WakizashiRecipe> FORM_WAKIZASHI =
        new RecipeSerializer<>(MapCodec.unit(WakizashiRecipe::new), StreamCodec.unit(new WakizashiRecipe()));

    @Override
    public void onInitialize() {
        var clayBlank = registerItem("clay_blank");
        firedMold = registerItem("fired_mold");
        advancedMold = registerItem("advanced_mold");
        Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, id("form_wakizashi"), FORM_WAKIZASHI);
        var tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id("dual_wield"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, tab, FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.slashblade_dual_wield"))
            .icon(firedMold::getDefaultInstance).build());
        CreativeModeTabEvents.modifyOutputEvent(tab).register(entries -> {
            List<ItemStack> samples = new ArrayList<>();
            for (var item : List.of(SBItems.SLASHBLADE_WOOD, SBItems.SLASHBLADE_BAMBOO,
                    SBItems.SLASHBLADE_SILVERBAMBOO, SBItems.SLASHBLADE_WHITE, SBItems.SLASHBLADE)) {
                samples.add(new ItemStack(item));
            }
            entries.getContext().holders().lookup(SlashBladeDefinition.REGISTRY_KEY).ifPresent(registry ->
                registry.listElements().sorted(Comparator.comparing(holder -> holder.key().identifier()))
                    .map(holder -> holder.value().getBlade()).forEach(samples::add));
            for (var sample : samples.reversed()) {
                if (BladeTypes.isUsable(sample)) {
                    entries.prepend(BladeTypes.isWakizashi(sample) ? sample : WakizashiRecipe.fromSample(sample));
                }
            }
            entries.prepend(advancedMold);
            entries.prepend(firedMold);
            entries.prepend(clayBlank);
        });
        DualWieldCombat.register();
        CombatGrowth.register();
        WakizashiCombat.register();
    }

    private static Item registerItem(String name) {
        var key = ResourceKey.create(Registries.ITEM, id(name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }
}
