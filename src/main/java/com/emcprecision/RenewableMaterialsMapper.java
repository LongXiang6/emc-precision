package com.emcprecision;

import com.mojang.logging.LogUtils;
import java.util.List;
import moze_intel.projecte.api.mapper.EMCMapper;
import moze_intel.projecte.api.mapper.IEMCMapper;
import moze_intel.projecte.api.mapper.collector.IMappingCollector;
import moze_intel.projecte.api.nss.NSSItem;
import moze_intel.projecte.api.nss.NormalizedSimpleStack;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

@EMCMapper
public final class RenewableMaterialsMapper implements IEMCMapper<NormalizedSimpleStack, Long> {
    private record Family(Item equivalent, String namespace, List<String> items) {}

    private static final List<Family> FAMILIES = List.of(
            family(Items.SAND, "biomesoplenty", "white_sand", "orange_sand", "black_sand"),
            family(Items.SAND, "natures_spirit", "pink_sand"),
            family(Items.SAND, "biomeswevegone", "blue_sand", "pink_sand", "windswept_sand"),
            family(Items.MOSS_BLOCK, "natures_spirit", "red_moss_block"),
            family(Items.DIRT, "regions_unexplored", "peat_dirt", "silt_dirt"),
            family(Items.DIRT, "biomeswevegone", "lush_dirt"),
            family(Items.DIRT, "aether", "aether_dirt"),
            family(Items.GRASS_BLOCK, "aether", "aether_grass_block"),
            family(Items.COBBLESTONE, "aether", "holystone"),
            family(Items.MOSSY_COBBLESTONE, "aether", "mossy_holystone"),
            family(Items.SOUL_SAND, "aether", "quicksoil"),
            family(Items.BAMBOO_BLOCK, "regions_unexplored", "bamboo_log"),
            family(Items.BAMBOO, "betternether", "nether_reed_stem"),
            family(Items.END_STONE, "betterend", "endstone_dust", "flavolite", "violecite",
                    "sandy_jadestone", "verdant_jadestone", "azure_jadestone"),
            family(Items.MYCELIUM, "betterend", "end_mycelium"),
            family(Items.MOSS_BLOCK, "betterend", "end_moss", "cave_moss", "crystal_moss",
                    "pink_moss", "amber_moss", "jungle_moss"),
            family(Items.APPLE, "fruitsdelight", "pear", "lychee", "mango", "persimmon", "peach", "orange",
                    "mangosteen", "kiwi", "fig", "durian"),
            family(Items.SWEET_BERRIES, "fruitsdelight", "hawberry", "bayberry", "blueberry", "cranberry", "lemon"),
            family(Items.WHEAT_SEEDS, "fruitsdelight", "lemon_seeds", "hamimelon_seeds"),
            family(Items.WHEAT_SEEDS, "kaleidoscope_cookery", "chili_seed", "lettuce_seed", "tea_seed"),
            family(Items.APPLE, "biomeswevegone", "green_apple"),
            family(Items.STRING, "kaleidoscope_cookery", "straw")
    );

    private static Family family(Item source, String mod, String... names) {
        return new Family(source, mod, List.of(names));
    }

    @Override
    public void addMappings(IMappingCollector<NormalizedSimpleStack, Long> collector,
            ReloadableServerResources resources, RegistryAccess registries, ResourceManager packs) {
        int registered = 0;
        for (var family : FAMILIES) {
            List<NormalizedSimpleStack> equivalent = List.of(NSSItem.createItem(family.equivalent()));
            for (String name : family.items()) {
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath(family.namespace(), name);
                var item = BuiltInRegistries.ITEM.getOptional(id);
                if (item.isPresent()) {
                    collector.addConversion(1, NSSItem.createItem(item.get()), equivalent);
                    registered++;
                }
            }
        }
        LogUtils.getLogger().info("EMC Precision registered {} material and crop equivalences", registered);
    }

    @Override
    public String getName() {
        return "EMC Precision Materials";
    }

    @Override
    public String getTranslationKey() {
        return "configuration.emc_precision.materials";
    }

    @Override
    public String getDescription() {
        return "Adds price anchors for explicitly selected terrain materials and renewable crops.";
    }
}
