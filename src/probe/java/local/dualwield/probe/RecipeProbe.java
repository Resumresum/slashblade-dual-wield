package local.dualwield.probe;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.HashSet;
import java.util.Objects;
import mods.flammpfeil.slashblade.registry.ComboStateRegistry;
import mods.flammpfeil.slashblade.item.SwordType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Unit;
import local.dualwield.BladeTypes;
import mods.flammpfeil.slashblade.capability.slashblade.CapabilitySlashBlade;
import mods.flammpfeil.slashblade.data.builtin.SlashBladeBuiltInRegistry;
import mods.flammpfeil.slashblade.registry.slashblade.SlashBladeDefinition;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.flag.FeatureFlags;

public final class RecipeProbe implements ClientModInitializer {
    private final Path game = Path.of(".").toAbsolutePath().normalize();
    private int ticks;
    private volatile boolean finished;
    private boolean recipeChecked;
    private volatile boolean combatComplete;
    private volatile CombatProbe combat;
    private volatile CatalogProbe catalog;
    private boolean catalogCombatComplete;
    private boolean growthComplete;
    private boolean parryComplete;
    private final java.util.List<String> growthResults = new java.util.ArrayList<>();
    private final java.util.List<String> bossResults = new java.util.ArrayList<>();
    private ParryArtProbe parry;

    @Override
    public void onInitializeClient() {
        String world = System.getProperty("dualwield.probe.world");
        if (world == null || !world.matches("recipe-[a-z0-9-]+")
                || !game.equals(Path.of("D:/Minecraft/slashblade-dual-wield-test/instance"))
                || Files.exists(game.resolve("saves").resolve(world))) {
            throw new IllegalStateException("Recipe probe requires a fresh isolated world");
        }
        var views = Boolean.getBoolean("dualwield.probe.views") ? new ViewProbe(game.resolve(world + ".txt")) : null;
        boolean updatesOnly = Boolean.getBoolean("dualwield.probe.updatesOnly");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (views != null && !finished) finished = views.tick(client);
            if (combat != null && !combatComplete && !finished) combat.clientTick(client);
            if (catalog != null && !finished) catalog.checkClientAssets(client);
            if (finished || ++ticks > 3600) {
                client.stop();
            } else if (ticks == 100) {
                client.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings(world, GameType.SURVIVAL,
                        new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), false,
                        WorldDataConfiguration.DEFAULT),
                    new WorldOptions(246801L, false, false), WorldPresets::createFlatWorldDimensions, client.screen);
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (views != null || finished || server.getPlayerList().getPlayers().isEmpty()) return;
            String result;
            try {
                var player = server.getPlayerList().getPlayers().getFirst();
                if (!recipeChecked) {
                    player.level().getGameRules().set(net.minecraft.world.level.gamerules.GameRules.SPAWN_MOBS, false, server);
                    player.level().getGameRules().set(
                        net.minecraft.world.level.gamerules.GameRules.NATURAL_HEALTH_REGENERATION, false, server);
                    run(player);
                    catalog = CatalogProbe.checkRecipes(player);
                    recipeChecked = true;
                }
                if (!catalog.assetsReady()) return;
                if (Boolean.getBoolean("dualwield.probe.combat")) {
                    if (!combatComplete && !updatesOnly) {
                        if (!player.connection.hasClientLoaded()) return;
                        if (combat == null) combat = new CombatProbe(player);
                        if (!combat.tick()) return;
                        Files.writeString(Path.of(System.getProperty("dualwield.probe.evidence"), "combat-result.txt"),
                            combat.results());
                        combatComplete = true;
                    }
                    if (!catalogCombatComplete && !updatesOnly) {
                        if (!catalog.checkLiveCounters(player)) return;
                        catalogCombatComplete = true;
                    }
                    if (!growthComplete) {
                        GrowthProbe.run(player, growthResults);
                        EquipmentProbe.run(player, growthResults);
                        Files.writeString(Path.of(System.getProperty("dualwield.probe.evidence"), "growth-result.txt"),
                            String.join("\n", growthResults));
                        growthComplete = true;
                    }
                    if (!parryComplete) {
                        if (parry == null) parry = new ParryArtProbe(player);
                        if (!parry.tick()) return;
                        Files.writeString(Path.of(System.getProperty("dualwield.probe.evidence"), "parry-result.txt"),
                            parry.results());
                        parryComplete = true;
                    }
                }
                BossCompatProbe.run(player, bossResults);
                Files.writeString(Path.of(System.getProperty("dualwield.probe.evidence"), "boss-result.txt"),
                    String.join("\n", bossResults));
                result = "PASS: clay blank, regular and advanced molds, copied enchantments and growth, independent saved blade state, exact sample returns and creative entries\n"
                    + catalog.writeReport();
                if (combat != null) result += "\n" + combat.results();
                if (!growthResults.isEmpty()) result += "\n" + String.join("\n", growthResults);
                if (parry != null) result += "\n" + parry.results();
                if (!bossResults.isEmpty()) result += "\n" + String.join("\n", bossResults);
            } catch (Throwable error) {
                error.printStackTrace();
                result = "FAIL: " + error;
            }
            try {
                Files.writeString(game.resolve(world + ".txt"), result);
            } catch (java.io.IOException error) {
                throw new IllegalStateException(error);
            }
            finished = true;
        });
    }

    private static void run(ServerPlayer player) {
        var menu = menu(player);
        for (int slot : new int[]{1, 3, 4, 6, 7, 8, 9}) menu.getSlot(slot).set(new ItemStack(Items.CLAY_BALL));
        menu.getSlot(2).set(new ItemStack(Items.NETHER_STAR));
        var blank = menu.getSlot(0).remove(1);
        require(blank.is(item("clay_blank")), "shaped clay blank did not craft");
        menu.getSlot(0).onTake(player, blank);
        for (int slot = 1; slot <= 9; slot++) {
            require(menu.getSlot(slot).getItem().isEmpty(), "blank recipe left an ingredient");
        }
        var mold = smelt(player, blank);
        var definitions = player.registryAccess().lookupOrThrow(SlashBladeDefinition.REGISTRY_KEY);
        ItemStack blade = definitions.getValueOrThrow(SlashBladeBuiltInRegistry.FOX_WHITE).getBlade();
        var state = CapabilitySlashBlade.getBladeState(blade).orElseThrow();
        state.setKillCount(31);
        state.setProudSoulCount(50);
        state.setRefine(10);
        blade.setDamageValue(5);
        blade.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.POWER), 2);
        var before = blade.copy();

        var normal = withBlade(player, blade, mold);
        var product = normal.getSlot(0).remove(1);
        checkProduct(product, before);
        normal.getSlot(0).onTake(player, product);
        require(ItemStack.isSameItemSameComponents(before, normal.getSlot(4).getItem()), "normal take changed the sample");
        require(normal.getSlot(9).getItem().isEmpty(), "normal take left the mold");
        require(withBlade(player, product, mold.copy()).getSlot(0).getItem().isEmpty(),
            "crafted wakizashi was accepted as a long-blade sample");

        var shift = withBlade(player, blade, mold.copyWithCount(2));
        for (int count = 1; count >= 0; count--) {
            checkProduct(shift.quickMoveStack(player, 0), before);
            require(ItemStack.isSameItemSameComponents(before, shift.getSlot(4).getItem()), "shift-click changed the sample");
            require(shift.getSlot(9).getItem().getCount() == count, "shift-click used the wrong number of molds");
        }
        require(shift.getSlot(0).getItem().isEmpty(), "result remained after the last mold");
        checkAdvancedMold(player, before);
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(Identifier.parse("slashblade_dual_wield:dual_wield"));
        require(tab != null, "dual wield creative tab missing");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(FeatureFlags.DEFAULT_FLAGS, true, player.registryAccess()));
        var entries = tab.getDisplayItems();
        require(entries.stream().anyMatch(stack -> stack.is(item("clay_blank"))), "creative clay blank missing");
        require(entries.stream().anyMatch(stack -> stack.is(item("fired_mold"))), "creative mold missing");
        require(entries.stream().anyMatch(stack -> stack.is(item("advanced_mold"))), "creative advanced mold missing");
        require(entries.stream().anyMatch(stack -> BladeTypes.isWakizashi(stack)
            && stack.get(DataComponents.ITEM_NAME) != null), "named creative wakizashi missing");
    }

    private static void checkAdvancedMold(ServerPlayer player, ItemStack original) {
        var upgrade = menu(player);
        upgrade.getSlot(4).set(new ItemStack(item("fired_mold")));
        upgrade.getSlot(9).set(new ItemStack(Items.NETHERITE_INGOT));
        var mold = upgrade.getSlot(0).remove(1);
        require(mold.is(item("advanced_mold")) && mold.getCount() == 1, "advanced mold recipe did not craft");
        upgrade.getSlot(0).onTake(player, mold);
        require(upgrade.getSlot(4).getItem().isEmpty() && upgrade.getSlot(9).getItem().isEmpty(),
            "advanced mold upgrade did not consume its ingredients");

        var sample = original.copy();
        var state = CapabilitySlashBlade.getBladeState(sample).orElseThrow();
        state.setDefaultBewitched(true);
        state.setBaseAttackModifier(15);
        state.setKillCount(1431);
        state.setMaxDamage(173);
        state.setColorCode(0x123456);
        state.setEffectColorInverse(true);
        state.setAdjust(new Vec3(1, 2, 3));
        state.setLastActionTime(987654);
        state.setTargetEntityId(12345);
        state.setOnClick(true);
        state.setAttackAmplifier(5);
        state.setFallDecreaseRate(0.75f);
        state.setComboSeq(ComboStateRegistry.getId(
            ComboStateRegistry.STANDBY));
        sample.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        sample.set(DataComponents.REPAIR_COST, 17);
        sample.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        sample.setDamageValue(37);
        sample.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 7);
        sample.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.BINDING_CURSE), 1);
        sample.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE), 1);
        var sourceId = UUID.randomUUID().toString();
        CustomData.update(DataComponents.CUSTOM_DATA, sample,
            tag -> {
                tag.putString("TsukiCapturedBlade", sourceId);
                tag.putInt("slashblade_dual_wield.killTenths", 7);
                tag.putInt("slashblade_dual_wield.soulTenths", 3);
            });
        var before = sample.copy();
        var normal = withBlade(player, sample, mold);
        var product = normal.getSlot(0).remove(1);
        checkAdvancedProduct(product, before);
        checkAdvancedProduct(roundTrip(player, product), before);
        normal.getSlot(0).onTake(player, product);
        require(ItemStack.isSameItemSameComponents(before, normal.getSlot(4).getItem()),
            "advanced normal take changed the sample");
        require(normal.getSlot(9).getItem().isEmpty(), "advanced normal take left the mold");
        require(withBlade(player, product, mold).getSlot(0).getItem().isEmpty(),
            "advanced mold accepted an already short blade");
        var identities = new HashSet<String>();
        identities.add(bladeIdentity(product));
        var shift = withBlade(player, before, mold.copyWithCount(2));
        for (int count = 1; count >= 0; count--) {
            var moved = shift.quickMoveStack(player, 0);
            checkAdvancedProduct(moved, before);
            require(identities.add(bladeIdentity(moved)), "advanced crafts reused a blade identity");
            require(ItemStack.isSameItemSameComponents(before, shift.getSlot(4).getItem()),
                "advanced shift-click changed the sample");
            require(shift.getSlot(9).getItem().getCount() == count, "advanced shift-click used the wrong number of molds");
        }
        require(shift.getSlot(0).getItem().isEmpty(), "advanced result remained after the last mold");

        var titled = before.copy();
        CapabilitySlashBlade.getBladeState(titled).orElseThrow().setDefaultBewitched(false);
        var title = Component.literal("Custom sample title");
        titled.set(DataComponents.CUSTOM_NAME, title);
        var titledProduct = withBlade(player, titled, mold).getSlot(0).getItem();
        checkAdvancedProduct(titledProduct, titled);
        require(titledProduct.getHoverName().equals(Component.translatable(
            "item.slashblade_dual_wield.wakizashi_from", title)), "custom sample title lost its wakizashi label");
        require(SwordType.from(titledProduct)
            .contains(SwordType.BEWITCHED), "custom-named sample lost bewitched eligibility");
    }

    private static void checkAdvancedProduct(ItemStack product, ItemStack before) {
        require(!product.isEmpty() && BladeTypes.isCraftedWakizashi(product), "advanced mold produced no short blade");
        var result = CapabilitySlashBlade.getBladeState(product).orElseThrow();
        var sample = CapabilitySlashBlade.getBladeState(before).orElseThrow();
        require(product.getEnchantments().equals(before.getEnchantments()), "advanced copy changed enchantments or levels");
        require(Objects.equals(product.get(DataComponents.UNBREAKABLE),
                before.get(DataComponents.UNBREAKABLE))
            && Objects.equals(product.get(DataComponents.REPAIR_COST),
                before.get(DataComponents.REPAIR_COST))
            && Objects.equals(product.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE),
                before.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)),
            "advanced copy changed persistent item settings");
        require(result.getKillCount() == sample.getKillCount() && result.getRefine() == sample.getRefine(),
            "advanced copy lost kills or refine");
        require(result.getProudSoulCount() == 0, "advanced copy inherited proud souls");
        require(result.getModel().equals(sample.getModel()) && result.getTexture().equals(sample.getTexture()),
            "advanced copy changed appearance");
        require(result.getSlashArtsKey().equals(sample.getSlashArtsKey()) && result.getComboRoot().equals(sample.getComboRoot()),
            "advanced copy changed SA or combo root");
        require(result.getColorCode() == sample.getColorCode() && result.isEffectColorInverse() == sample.isEffectColorInverse()
            && result.getCarryType() == sample.getCarryType() && result.getAdjust().equals(sample.getAdjust()),
            "advanced copy changed visual settings");
        require(result.getBaseAttackModifier() == sample.getBaseAttackModifier()
            && result.getMaxDamage() == sample.getMaxDamage() && result.getSpecialEffects().equals(sample.getSpecialEffects()),
            "advanced copy changed persistent blade settings");
        require(result.getTranslationKey().equals(sample.getTranslationKey())
            && result.isDefaultBewitched() == sample.isDefaultBewitched(), "advanced copy changed named blade type");
        require(SwordType.from(product).equals(
            SwordType.from(before)), "advanced copy changed blade eligibility");
        int expectedDamage = before.getMaxDamage() <= 0 ? 0
            : (int) Math.round((double) before.getDamageValue() / before.getMaxDamage() * product.getMaxDamage());
        require(product.getDamageValue() == expectedDamage, "advanced copy changed proportional wear");
        require(result.getLastActionTime() == 0 && result.getTargetEntityId() <= 0 && !result.onClick()
            && result.getAttackAmplifier() == 0 && result.getFallDecreaseRate() == 0
            && result.getComboSeq().equals(ComboStateRegistry.getId(
                ComboStateRegistry.NONE)), "advanced copy inherited transient combat state");
        var data = product.get(DataComponents.CUSTOM_DATA).copyTag();
        require(!data.contains("slashblade_dual_wield.killTenths") && !data.contains("slashblade_dual_wield.soulTenths"),
            "advanced copy inherited fractional growth");
        require(!bladeIdentity(product).equals(bladeIdentity(before)), "advanced copy reused source identity");
        if (before.get(DataComponents.CUSTOM_NAME) == null) {
            require(product.getHoverName().equals(Component.translatable(
                "item.slashblade_dual_wield.wakizashi_from", before.getItem().getName(before))),
                "advanced copy lost its short blade label");
        }
    }

    private static String bladeIdentity(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        require(data != null, "blade identity data missing");
        var id = data.copyTag().getStringOr("TsukiCapturedBlade", "");
        require(!id.isEmpty(), "blade identity missing");
        UUID.fromString(id);
        return id;
    }

    private static ItemStack roundTrip(ServerPlayer player, ItemStack stack) {
        var ops = player.registryAccess().createSerializationContext(NbtOps.INSTANCE);
        var encoded = ItemStack.CODEC.encodeStart(ops, stack).getOrThrow();
        var decoded = ItemStack.CODEC.parse(ops, encoded).getOrThrow();
        require(ItemStack.isSameItemSameComponents(stack, decoded), "crafted blade changed after serialization");
        return decoded;
    }

    private static ItemStack smelt(ServerPlayer player, ItemStack blank) {
        var level = player.level();
        var pos = player.blockPosition().above(3);
        level.setBlock(pos, Blocks.FURNACE.defaultBlockState(), Block.UPDATE_ALL);
        var furnace = (FurnaceBlockEntity) level.getBlockEntity(pos);
        furnace.setItem(0, blank);
        furnace.setItem(1, new ItemStack(Items.COAL));
        for (int i = 0; i < 200; i++) {
            FurnaceBlockEntity.serverTick(level, pos, level.getBlockState(pos), furnace);
        }
        require(furnace.getItem(0).isEmpty(), "furnace did not consume the blank");
        var result = furnace.removeItem(2, 1);
        require(result.is(item("fired_mold")), "furnace did not produce a mold");
        level.removeBlock(pos, false);
        return result;
    }

    private static CraftingMenu menu(ServerPlayer player) {
        return new CraftingMenu(1, player.getInventory(), ContainerLevelAccess.create(player.level(), player.blockPosition()));
    }

    static CraftingMenu withBlade(ServerPlayer player, ItemStack blade, ItemStack molds) {
        var menu = menu(player);
        menu.getSlot(4).set(blade.copy());
        menu.getSlot(9).set(molds.copy());
        return menu;
    }

    static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.getValue(Identifier.fromNamespaceAndPath("slashblade_dual_wield", id));
    }

    static void checkProduct(ItemStack product, ItemStack before) {
        var result = CapabilitySlashBlade.getBladeState(product).orElseThrow();
        var sample = CapabilitySlashBlade.getBladeState(before).orElseThrow();
        var name = Component.translatable("item.slashblade_dual_wield.wakizashi_from",
            before.getItem().getName(before));
        require(product.getHoverName().equals(name), "displayed name omitted the sample: " + product.getHoverName());
        var renamed = product.copy();
        var customName = Component.literal("Renamed wakizashi");
        renamed.set(DataComponents.CUSTOM_NAME, customName);
        require(renamed.getHoverName().equals(customName), "custom name was overridden");
        require(result.getModel().equals(sample.getModel()), "wrong model");
        require(result.getTexture().equals(sample.getTexture()), "wrong texture");
        require(result.getSlashArtsKey().equals(sample.getSlashArtsKey()), "wrong SA");
        require(result.getKillCount() == 0 && result.getProudSoulCount() == 0 && result.getRefine() == 0,
            "copied progression from sample");
        require(product.getDamageValue() == 0 && product.getEnchantments().isEmpty(), "copied damage or enchantments");
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
